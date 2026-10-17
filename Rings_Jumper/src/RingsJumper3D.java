import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

public class RingsJumper3D extends JPanel
        implements ActionListener, KeyListener,
        MouseListener, MouseMotionListener {

    static final int W = 1280, H = 720;
    static final Random RNG = new Random();

    final List<Ring> rings = new ArrayList<>();
    final List<Particle> particles = new ArrayList<>();
    final List<Star> stars = new ArrayList<>();

    javax.swing.Timer timer;
    BufferedImage frame;
    Cursor hiddenCursor;

    double x, y, z, vx, vy;
    double speed = 13, gravity = -15, flapForce = 8.8;
    double yaw, pitch, roll, elapsed;
    double powerTime;

    int score, best, level = 1, combo;
    boolean dead, mouseInitialized;

    int lastMouseX, lastMouseY;
    String power = "";
    long lastTime;

    final Color cyan = new Color(0, 255, 220);
    final Color purple = new Color(145, 65, 255);
    final Color panel = new Color(8, 12, 35, 220);

    static double rand(double a, double b) {
        return a + RNG.nextDouble() * (b - a);
    }

    static double clamp(double v, double a, double b) {
        return Math.max(a, Math.min(b, v));
    }

    static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(),
                c.getBlue(), (int) clamp(a, 0, 255));
    }

    static class V {
        double x, y, z;

        V(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        V add(V b) {
            return new V(x + b.x, y + b.y, z + b.z);
        }

        V sub(V b) {
            return new V(x - b.x, y - b.y, z - b.z);
        }

        V mul(double s) {
            return new V(x * s, y * s, z * s);
        }
    }

    static class Star {
        double x, y, z, size, phase;

        Star(double cameraZ) {
            reset(cameraZ);
        }

        void reset(double cameraZ) {
            x = rand(-85, 85);
            y = rand(-15, 45);
            z = cameraZ + rand(30, 220);
            size = rand(.5, 2.0);
            phase = rand(0, Math.PI * 2);
        }
    }

    static class Particle {
        V p, v;
        double life, maxLife, size;
        Color color;

        Particle(V p, Color color) {
            this.p = p;
            this.color = color;
            life = maxLife = rand(.3, .9);
            size = rand(.1, .4);
            v = new V(rand(-3, 3), rand(-3, 3), rand(-3, 1));
        }

        void update(double dt) {
            p = p.add(v.mul(dt));
            v.y -= 2 * dt;
            life -= dt;
        }
    }

    static class Ring {
        double x, y, z, radius, baseX, baseY, phase, moveSpeed;
        String type;
        boolean scored;

        Ring(double x, double y, double z,
             double radius, String type) {
            this.x = baseX = x;
            this.y = baseY = y;
            this.z = z;
            this.radius = radius;
            this.type = type;
            phase = rand(0, Math.PI * 2);
            moveSpeed = type.equals("moving") ? rand(.8, 2.0) : 0;
        }

        Color color() {
            switch (type) {
                case "moving": return new Color(255, 65, 90);
                case "bonus": return new Color(255, 215, 35);
                case "boss": return new Color(255, 30, 175);
                default: return new Color(0, 255, 210);
            }
        }

        void update(double dt) {
            if (type.equals("moving")) {
                phase += dt * moveSpeed;
                x = baseX + Math.cos(phase * .7) * 2.5;
                y = baseY + Math.sin(phase) * 2.0;
            }
        }
    }

    public RingsJumper3D() {
        setPreferredSize(new Dimension(W, H));
        setFocusable(true);
        setBackground(Color.BLACK);

        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);

        frame = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);

        try {
            BufferedImage blank = new BufferedImage(
                    16, 16, BufferedImage.TYPE_INT_ARGB);
            hiddenCursor = Toolkit.getDefaultToolkit()
                    .createCustomCursor(blank, new Point(0, 0), "hidden");
        } catch (Exception e) {
            hiddenCursor = Cursor.getDefaultCursor();
        }

        setCursor(hiddenCursor);

        for (int i = 0; i < 260; i++) {
            stars.add(new Star(0));
        }

        restartGame();

        lastTime = System.nanoTime();
        timer = new javax.swing.Timer(16, this);
        timer.start();
    }

    void restartGame() {
        x = 0;
        y = 7;
        z = 0;
        vx = vy = 0;

        yaw = pitch = roll = elapsed = 0;
        speed = 13;

        score = 0;
        level = 1;
        combo = 0;

        dead = false;
        power = "";
        powerTime = 0;

        rings.clear();
        particles.clear();

        for (int i = 0; i < 8; i++) {
            spawnRing(28 + i * 14);
        }

        for (Star s : stars) s.reset(z);

        mouseInitialized = false;
    }

    void spawnRing(double offset) {
        double r = RNG.nextDouble();
        String type = "normal";

        if (score > 15 && r < .18) {
            type = "moving";
        } else if (r < .30) {
            type = "bonus";
        } else if (score > 30 && score % 12 == 0) {
            type = "boss";
        }

        double radius;
        switch (type) {
            case "bonus": radius = 1.8; break;
            case "moving": radius = 3.0; break;
            case "boss": radius = 4.7; break;
            default: radius = Math.max(2, 3.6 - level * .12);
        }

        rings.add(new Ring(
                rand(-5.5, 5.5),
                rand(2.5, 13),
                z + offset,
                radius,
                type
        ));
    }

    void burst(V p, Color color, int count) {
        for (int i = 0; i < count; i++) {
            particles.add(new Particle(p, color));
        }
    }

    void flap() {
        if (dead) {
            restartGame();
            return;
        }

        vy = flapForce;
        burst(new V(x, y, z), cyan, 10);
    }

    void die() {
        if (dead) return;

        dead = true;
        vx = vy = 0;

        burst(new V(x, y, z), Color.YELLOW, 35);
        burst(new V(x, y, z), Color.ORANGE, 25);

        best = Math.max(best, score);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        long now = System.nanoTime();
        double dt = Math.min((now - lastTime) / 1e9, .035);
        lastTime = now;

        updateGame(dt);
        repaint();
    }

    void updateGame(double dt) {
        elapsed += dt;

        for (Particle p : particles) p.update(dt);
        particles.removeIf(p -> p.life <= 0);

        if (dead) return;

        vy += gravity * dt;
        vx += Math.sin(yaw) * speed * dt * .65;
        vx *= Math.pow(.25, dt);
        vx = clamp(vx, -10, 10);

        x += vx * dt;
        y += vy * dt;
        z += speed * dt;
        speed += dt * .07;

        x = clamp(x, -11, 11);
        roll += (-vx * .035 - roll) * Math.min(1, dt * 4);

        if (y <= -9.2 || y >= 25.7) {
            die();
            return;
        }

        int nextLevel = 1 + score / 8;

        if (nextLevel != level) {
            level = nextLevel;
            burst(new V(x, y, z), cyan, 22);
        }

        if (powerTime > 0) {
            powerTime -= dt;
            if (powerTime <= 0) power = "";
        }

        for (int i = rings.size() - 1; i >= 0; i--) {
            Ring r = rings.get(i);
            r.update(dt);

            double dx = x - r.x;
            double dy = y - r.y;
            double dz = z - r.z;

            if (Math.abs(dz) < 1.15) {
                double d = Math.sqrt(dx * dx + dy * dy);

                if (!r.scored && d < r.radius - .65) {
                    r.scored = true;

                    int add = r.type.equals("bonus") ? 3
                            : r.type.equals("boss") ? 5 : 1;

                    if (power.equals("BONUS x3")) add *= 3;

                    score += add;
                    combo++;

                    burst(new V(r.x, r.y, r.z), r.color(), 22);

                    if (r.type.equals("bonus")) {
                        power = "BONUS x3";
                        powerTime = 5;
                    } else if (r.type.equals("boss")) {
                        power = "BOSS RING!";
                        powerTime = 3;
                    }
                } else if (!r.scored && d < r.radius + .65) {
                    die();
                    return;
                }
            }

            if (r.z < z - 25) {
                rings.remove(i);
                spawnRing(90);
            }
        }

        for (Star s : stars) {
            if (s.z < z + 4) s.reset(z);
        }
    }

    // Kamera jest graczem. Nie ma modelu ptaka ani skrzydeł.
    V cameraSpace(V p) {
        double dx = p.x - x;
        double dy = p.y - y;
        double dz = p.z - z;

        double cy = Math.cos(yaw), sy = Math.sin(yaw);
        double rx = dx * cy - dz * sy;
        double rz = dx * sy + dz * cy;

        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        double ry = dy * cp - rz * sp;
        double depth = dy * sp + rz * cp;

        double cr = Math.cos(roll), sr = Math.sin(roll);

        return new V(
                rx * cr - ry * sr,
                rx * sr + ry * cr,
                depth
        );
    }

    Point project(V p) {
        V q = cameraSpace(p);

        if (q.z <= .15) return new Point(-10000, -10000);

        double focal = 700;

        return new Point(
                (int) (W / 2.0 + q.x * focal / q.z),
                (int) (H / 2.0 - q.y * focal / q.z)
        );
    }

    void line3D(Graphics2D g, V a, V b, Color c) {
        V ca = cameraSpace(a), cb = cameraSpace(b);

        if (ca.z <= .15 || cb.z <= .15) return;

        Point pa = project(a), pb = project(b);
        g.setColor(c);
        g.drawLine(pa.x, pa.y, pb.x, pb.y);
    }

    void drawRingLines(Graphics2D g, Point[] pts) {
        for (int i = 0; i < pts.length; i++) {
            Point a = pts[i], b = pts[(i + 1) % pts.length];

            if (a.x == -10000 || b.x == -10000) continue;

            g.drawLine(a.x, a.y, b.x, b.y);
        }
    }

    void drawRing(Graphics2D g, Ring r) {
        int n = 100;
        Point[] pts = new Point[n];

        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            pts[i] = project(new V(
                    r.x + Math.cos(a) * r.radius,
                    r.y + Math.sin(a) * r.radius,
                    r.z
            ));
        }

        Color c = r.color();

        g.setStroke(new BasicStroke(14, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g.setColor(alpha(c, 25));
        drawRingLines(g, pts);

        g.setStroke(new BasicStroke(7, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g.setColor(alpha(c, 65));
        drawRingLines(g, pts);

        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g.setColor(c);
        drawRingLines(g, pts);

        g.setStroke(new BasicStroke(1));
    }

    void drawBackground(Graphics2D g) {
        g.setPaint(new GradientPaint(
                0, 0, new Color(24, 15, 75),
                0, H, new Color(2, 5, 22)
        ));
        g.fillRect(0, 0, W, H);

        g.setPaint(new RadialGradientPaint(
                new Point(W / 4, H / 3), 450f,
                new float[]{0f, 1f},
                new Color[]{
                        new Color(100, 35, 190, 65),
                        new Color(20, 0, 60, 0)
                }
        ));
        g.fillRect(0, 0, W, H);

        g.setPaint(new RadialGradientPaint(
                new Point(W * 3 / 4, H / 2), 380f,
                new float[]{0f, 1f},
                new Color[]{
                        new Color(0, 125, 200, 45),
                        new Color(0, 0, 40, 0)
                }
        ));
        g.fillRect(0, 0, W, H);

        for (Star s : stars) {
            Point p = project(new V(s.x, s.y, s.z));

            if (p.x < 0 || p.x >= W || p.y < 0 || p.y >= H)
                continue;

            double twinkle = .65 + .35 *
                    Math.sin(elapsed * 2 + s.phase);

            int size = Math.max(1, (int) (s.size * twinkle));

            g.setColor(new Color(200, 220, 255, 210));
            g.fillOval(p.x, p.y, size, size);
        }
    }

    void drawWorld(Graphics2D g) {
        drawBackground(g);

        int start = (int) (z / 10) * 10;

        for (int zz = start; zz < z + 190; zz += 10) {
            double pulse = .5 + .5 * Math.sin(elapsed * 2 + zz * .03);

            Color c = new Color(
                    25,
                    65 + (int) (35 * pulse),
                    135 + (int) (70 * pulse)
            );

            line3D(g, new V(-15, -9.5, zz),
                    new V(15, -9.5, zz), c);
            line3D(g, new V(-15, 26, zz),
                    new V(15, 26, zz), c);
            line3D(g, new V(-15, -9.5, zz),
                    new V(-15, 26, zz), c);
            line3D(g, new V(15, -9.5, zz),
                    new V(15, 26, zz), c);
        }

        List<Ring> ordered = new ArrayList<>(rings);
        ordered.sort((a, b) -> Double.compare(b.z, a.z));

        for (Ring r : ordered) drawRing(g, r);

        for (Particle p : particles) {
            Point q = project(p.p);

            if (q.x < -50 || q.x > W + 50 ||
                    q.y < -50 || q.y > H + 50) continue;

            int size = Math.max(2, (int) (p.size * 13));

            g.setColor(alpha(p.color,
                    (int) (255 * p.life / p.maxLife)));

            g.fillOval(q.x - size / 2, q.y - size / 2,
                    size, size);
        }

        drawCrosshair(g);
    }

    void drawCrosshair(Graphics2D g) {
        g.setColor(new Color(190, 255, 255, 210));
        g.setStroke(new BasicStroke(1.5f));

        g.drawOval(W / 2 - 5, H / 2 - 5, 10, 10);
        g.drawLine(W / 2 - 14, H / 2, W / 2 - 7, H / 2);
        g.drawLine(W / 2 + 7, H / 2, W / 2 + 14, H / 2);
        g.drawLine(W / 2, H / 2 - 14, W / 2, H / 2 - 7);
        g.drawLine(W / 2, H / 2 + 7, W / 2, H / 2 + 14);

        g.setStroke(new BasicStroke(1));
    }

    void drawPanel(Graphics2D g, int px, int py, int pw, int ph) {
        g.setColor(panel);
        g.fillRoundRect(px, py, pw, ph, 18, 18);

        g.setStroke(new BasicStroke(1.3f));
        g.setColor(new Color(0, 240, 220, 115));
        g.drawRoundRect(px, py, pw, ph, 18, 18);
        g.setStroke(new BasicStroke(1));
    }

    void drawHUD(Graphics2D g) {
        // Panel punktów
        drawPanel(g, 24, 22, 260, 128);

        g.setFont(new Font("Dialog", Font.BOLD, 13));
        g.setColor(cyan);
        g.drawString("R I N G S   J U M P E R", 42, 46);

        g.setFont(new Font("Dialog", Font.BOLD, 30));
        g.setColor(Color.WHITE);
        g.drawString(String.valueOf(score), 42, 83);

        g.setFont(new Font("Dialog", Font.PLAIN, 14));
        g.setColor(new Color(190, 205, 240));
        g.drawString("SCORE", 44, 105);
        g.drawString("BEST  " + best, 42, 132);

        // Panel poziomu
        drawPanel(g, W - 235, 22, 210, 105);

        g.setFont(new Font("Dialog", Font.BOLD, 13));
        g.setColor(cyan);
        g.drawString("FLIGHT STATUS", W - 215, 47);

        g.setFont(new Font("Dialog", Font.BOLD, 24));
        g.setColor(Color.WHITE);
        g.drawString("LEVEL " + level, W - 215, 76);

        g.setColor(new Color(35, 48, 75));
        g.fillRoundRect(W - 215, 91, 170, 8, 8, 8);

        g.setColor(cyan);
        int progress = (score % 8) * 170 / 8;
        g.fillRoundRect(W - 215, 91, progress, 8, 8, 8);

        // Bonus
        if (!power.isEmpty()) {
            drawPanel(g, W / 2 - 115, 28, 230, 48);

            g.setFont(new Font("Dialog", Font.BOLD, 16));
            g.setColor(Color.YELLOW);
            centered(g, power + "  " +
                    (int) Math.ceil(powerTime) + "s", 58);
        }

        // Instrukcje na dole
        drawPanel(g, W / 2 - 230, H - 57, 460, 38);

        g.setFont(new Font("Dialog", Font.BOLD, 12));
        g.setColor(new Color(210, 225, 255));
        centered(g, "MOUSE  STEER     |     LMB  FLAP     |     ESC  EXIT",
                H - 33);

        if (combo >= 3 && !dead) {
            g.setFont(new Font("Dialog", Font.BOLD, 27));
            g.setColor(Color.YELLOW);
            centered(g, "COMBO x" + combo, 190);
        }

        if (dead) drawGameOver(g);
    }

    void drawGameOver(Graphics2D g) {
        g.setColor(new Color(2, 4, 20, 225));
        g.fillRect(0, 0, W, H);

        int pw = 540, ph = 330;
        int px = (W - pw) / 2;
        int py = (H - ph) / 2;

        g.setColor(new Color(8, 12, 35, 245));
        g.fillRoundRect(px, py, pw, ph, 30, 30);

        g.setStroke(new BasicStroke(3));
        g.setColor(new Color(0, 255, 220, 210));
        g.drawRoundRect(px, py, pw, ph, 30, 30);

        g.setStroke(new BasicStroke(1));
        g.setFont(new Font("Dialog", Font.BOLD, 13));
        g.setColor(new Color(255, 75, 115));
        centered(g, "FLIGHT TERMINATED", py + 48);

        g.setFont(new Font("Dialog", Font.BOLD, 48));
        g.setColor(Color.WHITE);
        centered(g, "GAME OVER", py + 110);

        g.setFont(new Font("Dialog", Font.BOLD, 22));
        g.setColor(cyan);
        centered(g, "SCORE  " + score + "     BEST  " + best,
                py + 160);

        int bx = px + 60, by = py + 195, bw = pw - 120;

        g.setColor(new Color(20, 35, 55));
        g.fillRoundRect(bx, by, bw, 48, 14, 14);

        g.setColor(new Color(0, 220, 190, 40));
        g.fillRoundRect(bx, by, bw, 48, 14, 14);

        g.setFont(new Font("Dialog", Font.BOLD, 15));
        g.setColor(Color.WHITE);
        centered(g, "CLICK LEFT MOUSE TO PLAY AGAIN", by + 29);

        g.setFont(new Font("Dialog", Font.PLAIN, 14));
        g.setColor(new Color(165, 180, 210));
        centered(g, "ESC  -  EXIT", py + 290);
    }

    void centered(Graphics2D g, String text, int yy) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (W - fm.stringWidth(text)) / 2, yy);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D g = frame.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);

        drawWorld(g);
        drawHUD(g);
        g.dispose();

        Graphics2D out = (Graphics2D) graphics.create();
        out.drawImage(frame, 0, 0, getWidth(), getHeight(), null);
        out.dispose();
    }

    @Override
    public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
        setCursor(hiddenCursor);

        if (SwingUtilities.isLeftMouseButton(e)) flap();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        updateMouse(e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        updateMouse(e);
    }

    void updateMouse(MouseEvent e) {
        if (dead) return;

        if (!mouseInitialized) {
            lastMouseX = e.getX();
            lastMouseY = e.getY();
            mouseInitialized = true;
            return;
        }

        int dx = e.getX() - lastMouseX;
        int dy = e.getY() - lastMouseY;

        yaw += dx * .0035;
        pitch = clamp(pitch - dy * .003, -1.0, 1.0);

        vx = clamp(vx + dx * .075, -10, 10);
        vy = clamp(vy - dy * .025, -14, 14);

        lastMouseX = e.getX();
        lastMouseY = e.getY();
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            System.exit(0);
        }
    }

    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {
        setCursor(hiddenCursor);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame window = new JFrame("Rings Jumper 3D");

            RingsJumper3D game = new RingsJumper3D();

            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            window.setUndecorated(true);
            window.setContentPane(game);
            window.setExtendedState(JFrame.MAXIMIZED_BOTH);
            window.setVisible(true);

            game.setCursor(game.hiddenCursor);
            game.requestFocusInWindow();

            window.addWindowFocusListener(new WindowAdapter() {
                @Override
                public void windowGainedFocus(WindowEvent e) {
                    game.setCursor(game.hiddenCursor);
                    game.requestFocusInWindow();
                    game.mouseInitialized = false;
                }
            });
        });
    }
}