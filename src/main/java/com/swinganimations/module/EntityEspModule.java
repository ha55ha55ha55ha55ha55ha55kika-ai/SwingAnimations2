package com.swinganimations.module;

import com.swinganimations.render.Batch;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * EntityESP: подсветка друзей, врагов (игроков), мобов и предметов. Для каждой группы свой стиль:
 * Box (3D-обводка по хитбоксу: сверху затухает, снизу светится), Fill (анимированная заливка) или оба сразу.
 * Друзья всегда зелёные. Без GLSL-шейдеров: только градиентные треугольники, поэтому работает и на Pojav.
 * Друзья добавляются клавишей «Добавить/убрать друга» (на игрока в прицеле), список хранится в конфиге модуля.
 */
public final class EntityEspModule extends Module {
    private static final int FRIEND_RGB = 0x3CFF6E;
    private static final int MOB_RGB = 0xFF9A3C;
    private static final int ITEM_RGB = 0x7FD8FF;

    private final Setting.Mode friendsStyle = add(new Setting.Mode("friends_style", "Friends", "Off", "Box", "Fill", "Both"));
    private final Setting.Mode enemiesStyle = add(new Setting.Mode("enemies_style", "Enemies (players)", "Off", "Box", "Fill", "Both"));
    private final Setting.Mode mobsStyle = add(new Setting.Mode("mobs_style", "Mobs", "Off", "Box", "Fill", "Both"));
    private final Setting.Mode itemsStyle = add(new Setting.Mode("items_style", "Items", "Off", "Box", "Fill", "Both"));
    private final Setting.Bool walls = add(new Setting.Bool("walls", "Through walls", true));
    private final Setting.Num range = add(new Setting.Num("range", "Range", 64f, 8f, 160f, 4f));
    private final Setting.Num opacity = add(new Setting.Num("opacity", "Opacity", 0.8f, 0.1f, 1f, 0.05f));
    private final Setting.Num speed = add(new Setting.Num("speed", "Animation speed", 1f, 0.2f, 3f, 0.1f));
    private final ColorSettings color = new ColorSettings(this);
    private final Setting.Str friendList = add(new Setting.Str("friend_list", "Friends list", ""));

    private final Set<String> friendSet = new HashSet<>();
    private String friendSrc = null;

    public EntityEspModule() {
        super("entity_esp", "EntityESP", "render");
        friendsStyle.index = 1;
        enemiesStyle.index = 1;
    }

    @Override
    public boolean wantsWorldRender() { return true; }

    // ---------------- друзья ----------------

    private void syncFriends() {
        if (friendList.value.equals(friendSrc)) return;
        friendSrc = friendList.value;
        friendSet.clear();
        for (String s : friendSrc.split(",")) {
            String n = s.trim().toLowerCase(Locale.ROOT);
            if (!n.isEmpty()) friendSet.add(n);
        }
    }

    public boolean isFriend(String name) {
        syncFriends();
        return friendSet.contains(name.toLowerCase(Locale.ROOT));
    }

    /** Добавляет или убирает друга. Возвращает true, если игрок теперь друг. */
    public boolean toggleFriend(String name) {
        syncFriends();
        String n = name.toLowerCase(Locale.ROOT);
        boolean added;
        if (friendSet.contains(n)) {
            friendSet.remove(n);
            added = false;
        } else {
            friendSet.add(n);
            added = true;
        }
        friendList.value = String.join(",", friendSet);
        friendSrc = friendList.value;
        ModuleManager.save();
        return added;
    }

    // ---------------- рендер ----------------

    @Override
    public void render(RenderContext c) {
        MinecraftClient mc = c.mc;
        if (mc.world == null || mc.player == null) return;
        Vec3d cam = c.camera.getCameraPos();
        double r2 = (double) range.value * range.value;
        float op = opacity.value;
        float t = c.now / 1000f * speed.value;
        int enemyRgb = color.rgb(c.now, 0f);
        Batch tris = null;
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || e.isRemoved()) continue;
            int style;
            int rgb;
            if (e instanceof PlayerEntity p) {
                if (p.isSpectator()) continue;
                if (isFriend(p.getName().getString())) {
                    style = friendsStyle.index;
                    rgb = FRIEND_RGB;
                } else {
                    style = enemiesStyle.index;
                    rgb = enemyRgb;
                }
            } else if (e instanceof ItemEntity) {
                style = itemsStyle.index;
                rgb = ITEM_RGB;
            } else if (e instanceof LivingEntity && !(e instanceof ArmorStandEntity)) {
                style = mobsStyle.index;
                rgb = MOB_RGB;
            } else {
                continue;
            }
            if (style == 0 || e.squaredDistanceTo(cam) > r2) continue;
            if (tris == null) {
                Batch.begin(!walls.value, true);
                tris = Batch.triangles();
            }
            Box b = box(e, c.tickDelta);
            if (style == 1 || style == 3) boxStyle(tris, c.m, b, rgb, op);
            if (style == 2 || style == 3) fillStyle(tris, c.m, b, rgb, op, t + (e.getId() % 17) * 0.37f);
        }
        if (tris != null) {
            tris.flush();
            Batch.end();
        }
    }

    private static Box box(Entity e, float td) {
        double dx = MathHelper.lerp(td, e.lastRenderX, e.getX()) - e.getX();
        double dy = MathHelper.lerp(td, e.lastRenderY, e.getY()) - e.getY();
        double dz = MathHelper.lerp(td, e.lastRenderZ, e.getZ()) - e.getZ();
        return e.getBoundingBox().offset(dx, dy, dz).expand(0.02);
    }

    // ---------------- примитивы ----------------

    private static void quad(Batch t, Matrix4f m,
                             double ax, double ay, double az, int ac,
                             double bx, double by, double bz, int bc,
                             double cx, double cy, double cz, int cc,
                             double dx, double dy, double dz, int dc) {
        t.v(m, ax, ay, az, ac);
        t.v(m, bx, by, bz, bc);
        t.v(m, cx, cy, cz, cc);
        t.v(m, ax, ay, az, ac);
        t.v(m, cx, cy, cz, cc);
        t.v(m, dx, dy, dz, dc);
    }

    /** Вертикальная грань между двумя точками основания: низ cLo, верх cHi. */
    private static void wall(Batch t, Matrix4f m, double x0, double z0, double x1, double z1,
                             double y0, double y1, int cLo, int cHi) {
        quad(t, m, x0, y0, z0, cLo, x1, y0, z1, cLo, x1, y1, z1, cHi, x0, y1, z0, cHi);
    }

    private static void vEdge(Batch t, Matrix4f m, double cx, double cz, double y0, double y1, double w, int cLo, int cHi) {
        quad(t, m, cx - w, y0, cz, cLo, cx + w, y0, cz, cLo, cx + w, y1, cz, cHi, cx - w, y1, cz, cHi);
        quad(t, m, cx, y0, cz - w, cLo, cx, y0, cz + w, cLo, cx, y1, cz + w, cHi, cx, y1, cz - w, cHi);
    }

    private static void hEdgeX(Batch t, Matrix4f m, double x0, double x1, double y, double z, double w, int c) {
        quad(t, m, x0, y, z - w, c, x1, y, z - w, c, x1, y, z + w, c, x0, y, z + w, c);
        quad(t, m, x0, y - w, z, c, x1, y - w, z, c, x1, y + w, z, c, x0, y + w, z, c);
    }

    private static void hEdgeZ(Batch t, Matrix4f m, double x, double y, double z0, double z1, double w, int c) {
        quad(t, m, x - w, y, z0, c, x - w, y, z1, c, x + w, y, z1, c, x + w, y, z0, c);
        quad(t, m, x, y - w, z0, c, x, y - w, z1, c, x, y + w, z1, c, x, y + w, z0, c);
    }

    // ---------------- Box: обводка, сверху затухает, снизу светится ----------------

    private static void boxStyle(Batch t, Matrix4f m, Box b, int rgb, float op) {
        double x0 = b.minX, x1 = b.maxX, y0 = b.minY, y1 = b.maxY, z0 = b.minZ, z1 = b.maxZ;
        int lo = Batch.argb(rgb, 0.45f * op);
        int hi = Batch.argb(rgb, 0.02f * op);
        wall(t, m, x0, z0, x1, z0, y0, y1, lo, hi);
        wall(t, m, x1, z0, x1, z1, y0, y1, lo, hi);
        wall(t, m, x1, z1, x0, z1, y0, y1, lo, hi);
        wall(t, m, x0, z1, x0, z0, y0, y1, lo, hi);

        // Свечение у основания: мягкое кольцо, которое растёт наружу и гаснет.
        double g = 0.22;
        int in = Batch.argb(rgb, 0.40f * op);
        int out = Batch.argb(rgb, 0f);
        double yb = y0 + 0.003;
        quad(t, m, x0, yb, z0, in, x1, yb, z0, in, x1 + g, yb, z0 - g, out, x0 - g, yb, z0 - g, out);
        quad(t, m, x1, yb, z0, in, x1, yb, z1, in, x1 + g, yb, z1 + g, out, x1 + g, yb, z0 - g, out);
        quad(t, m, x1, yb, z1, in, x0, yb, z1, in, x0 - g, yb, z1 + g, out, x1 + g, yb, z1 + g, out);
        quad(t, m, x0, yb, z1, in, x0, yb, z0, in, x0 - g, yb, z0 - g, out, x0 - g, yb, z1 + g, out);

        // Рёбра: низ яркий, верх тусклый.
        double w = 0.016;
        int eLo = Batch.argb(rgb, 0.95f * op);
        int eHi = Batch.argb(rgb, 0.14f * op);
        vEdge(t, m, x0, z0, y0, y1, w, eLo, eHi);
        vEdge(t, m, x1, z0, y0, y1, w, eLo, eHi);
        vEdge(t, m, x1, z1, y0, y1, w, eLo, eHi);
        vEdge(t, m, x0, z1, y0, y1, w, eLo, eHi);
        hEdgeX(t, m, x0, x1, y0, z0, w, eLo);
        hEdgeX(t, m, x0, x1, y0, z1, w, eLo);
        hEdgeZ(t, m, x0, y0, z0, z1, w, eLo);
        hEdgeZ(t, m, x1, y0, z0, z1, w, eLo);
        hEdgeX(t, m, x0, x1, y1, z0, w, eHi);
        hEdgeX(t, m, x0, x1, y1, z1, w, eHi);
        hEdgeZ(t, m, x0, y1, z0, z1, w, eHi);
        hEdgeZ(t, m, x1, y1, z0, z1, w, eHi);
    }

    // ---------------- Fill: анимированная заливка ----------------

    private static void fillStyle(Batch t, Matrix4f m, Box b, int rgb, float op, float time) {
        double x0 = b.minX, x1 = b.maxX, y0 = b.minY, y1 = b.maxY, z0 = b.minZ, z1 = b.maxZ;
        float pulse = 0.65f + 0.35f * (float) Math.sin(time * 3.8f);
        float bandAt = time * 0.35f - (float) Math.floor(time * 0.35f);
        int n = 8;
        for (int j = 0; j < n; j++) {
            float ua = j / (float) n, ub = (j + 1) / (float) n;
            double ya = y0 + (y1 - y0) * ua, yb = y0 + (y1 - y0) * ub;
            int ca = fillColor(rgb, op, pulse, ua, bandAt);
            int cb = fillColor(rgb, op, pulse, ub, bandAt);
            quad(t, m, x0, ya, z0, ca, x1, ya, z0, ca, x1, yb, z0, cb, x0, yb, z0, cb);
            quad(t, m, x1, ya, z0, ca, x1, ya, z1, ca, x1, yb, z1, cb, x1, yb, z0, cb);
            quad(t, m, x1, ya, z1, ca, x0, ya, z1, ca, x0, yb, z1, cb, x1, yb, z1, cb);
            quad(t, m, x0, ya, z1, ca, x0, ya, z0, ca, x0, yb, z0, cb, x0, yb, z1, cb);
        }
        int top = Batch.argb(rgb, 0.10f * op * pulse);
        int bottom = Batch.argb(rgb, 0.30f * op * pulse);
        quad(t, m, x0, y1, z0, top, x1, y1, z0, top, x1, y1, z1, top, x0, y1, z1, top);
        quad(t, m, x0, y0, z0, bottom, x1, y0, z0, bottom, x1, y0, z1, bottom, x0, y0, z1, bottom);
    }

    /** Цвет точки по высоте u (0..1): тёмнее к верху, плюс бегущая светлая полоса. */
    private static int fillColor(int rgb, float op, float pulse, float u, float bandAt) {
        float d = Math.abs(u - bandAt);
        d = Math.min(d, 1f - d);
        float band = (float) Math.exp(-d * d / 0.02f);
        float a = op * (0.20f * pulse + 0.40f * band) * (1f - 0.45f * u);
        return Batch.argb(Batch.mix(rgb, 0xFFFFFF, 0.5f * band), a);
    }
}
