from ursina import *
import random
import math

app = Ursina()

window.title = "Rings Jumper 3D"
window.borderless = False
window.fullscreen = True
window.exit_button.visible = False
window.cog_button.visible = False
window.fps_counter.enabled = False

def C(r, g, b):
    return color.rgb(r, g, b)

Sky(color=color.rgb(25, 35, 90))
scene.fog_density = 0.018
scene.fog_color = color.rgb(25, 35, 90)

AmbientLight(color=color.rgba(110, 110, 140, 255))
DirectionalLight(y=20, x=10, z=5)

for _ in range(60):
    Entity(
        model='sphere',
        scale=random.uniform(0.4, 1.5),
        position=(
            random.uniform(-80, 80),
            random.uniform(15, 45),
            random.uniform(-30, 250)
        ),
        color=C(200, 210, 255),
        alpha=0.35
    )

for _ in range(30):
    Entity(
        model='cube',
        scale=(
            random.uniform(4, 9),
            0.8,
            random.uniform(3, 6)
        ),
        position=(
            random.uniform(-50, 50),
            random.uniform(12, 28),
            random.uniform(-20, 200)
        ),
        color=C(255, 255, 255),
        alpha=0.55
    )

FLOOR_Y = -9.5
CEILING_Y = 26

score = 0
best = 0
level = 1
combo = 0

game_over = False

forward_speed = 13
base_speed = 13

gravity = -15
flap_force = 8.8

powerup_active = None
powerup_timer = 0


class Puff(Entity):
    def __init__(self, pos, col, scale=0.2, life=0.7, vel=None):
        super().__init__(
            model='sphere',
            color=col,
            scale=scale,
            position=pos
        )

        self.velocity = (
            vel if vel else Vec3(
                random.uniform(-2, 2),
                random.uniform(-1, 2),
                random.uniform(-3, -0.5)
            )
        )

        self.life = life
        self.start = scale

    def update(self):
        self.position += self.velocity * time.dt
        self.life -= time.dt

        self.scale = self.start * max(
            0,
            self.life / 0.7
        )

        self.alpha = max(
            0,
            self.life
        )

        if self.life <= 0:
            destroy(self)


def burst(pos, col, n=12, spread=2):
    for _ in range(n):
        Puff(
            pos,
            col,
            scale=random.uniform(0.12, 0.38),
            life=random.uniform(0.3, 0.8),
            vel=Vec3(
                random.uniform(-spread, spread),
                random.uniform(-spread, spread),
                random.uniform(-spread, spread)
            )
        )


class Bird3D:
    def __init__(self, parent):
        self.root = parent

        self.body = Entity(
            parent=parent,
            model='sphere',
            color=C(255, 220, 0),
            scale=(1.0, 0.75, 1.3)
        )

        self.head = Entity(
            parent=parent,
            model='sphere',
            color=C(255, 235, 80),
            scale=(0.7, 0.7, 0.75),
            position=(0.6, 0.25, 0)
        )

        self.beak_top = Entity(
            parent=parent,
            model='cube',
            color=C(255, 130, 0),
            scale=(0.6, 0.18, 0.32),
            position=(1.05, 0.25, 0)
        )

        self.beak_bot = Entity(
            parent=parent,
            model='cube',
            color=C(220, 100, 0),
            scale=(0.5, 0.12, 0.28),
            position=(1.0, 0.1, 0)
        )

        self.eye_l = Entity(
            parent=parent,
            model='sphere',
            color=C(0, 0, 0),
            scale=(0.14, 0.14, 0.14),
            position=(0.75, 0.45, 0.22)
        )

        self.eye_r = Entity(
            parent=parent,
            model='sphere',
            color=C(0, 0, 0),
            scale=(0.14, 0.14, 0.14),
            position=(0.75, 0.45, -0.22)
        )

        self.eye_glint = Entity(
            parent=parent,
            model='sphere',
            color=C(255, 255, 255),
            scale=(0.05, 0.05, 0.05),
            position=(0.8, 0.5, 0.25)
        )

        self.wing_l = Entity(
            parent=parent,
            model='cube',
            color=C(255, 245, 140),
            scale=(0.08, 0.12, 1.1),
            position=(0, 0, 0.75)
        )

        self.wing_r = Entity(
            parent=parent,
            model='cube',
            color=C(255, 245, 140),
            scale=(0.08, 0.12, 1.1),
            position=(0, 0, -0.75)
        )

        self.tail = Entity(
            parent=parent,
            model='cube',
            color=C(255, 200, 20),
            scale=(0.5, 0.08, 0.8),
            position=(-0.7, 0, 0)
        )

        self.leg_l = Entity(
            parent=parent,
            model='cube',
            color=C(255, 100, 0),
            scale=(0.08, 0.3, 0.08),
            position=(-0.2, -0.4, 0.15)
        )

        self.leg_r = Entity(
            parent=parent,
            model='cube',
            color=C(255, 100, 0),
            scale=(0.08, 0.3, 0.08),
            position=(-0.2, -0.4, -0.15)
        )

        self.flap_phase = 0

    def flap_anim(self):
        self.wing_l.rotation_z = 55
        self.wing_r.rotation_z = -55
        self.body.scale_y = 0.9

        invoke(
            lambda: setattr(
                self.wing_l,
                'rotation_z',
                0
            ),
            delay=0.14
        )

        invoke(
            lambda: setattr(
                self.wing_r,
                'rotation_z',
                0
            ),
            delay=0.14
        )

        invoke(
            lambda: setattr(
                self.body,
                'scale_y',
                0.75
            ),
            delay=0.14
        )

    def update_flap(self, vel_y):
        self.flap_phase += time.dt * 12
        wave = math.sin(self.flap_phase) * 18

        if not game_over:
            self.wing_l.rotation_z = wave
            self.wing_r.rotation_z = -wave

        self.root.rotation_z = -vel_y * 2.5
        self.root.rotation_x = vel_y * 1.2


bird_collider = Entity(
    model='cube',
    scale=(0.7, 0.55, 0.7),
    position=(0, 7, 0),
    collider='box',
    visible=False
)

bird_collider.vel_y = 0
bird_collider.vel_x = 0

bird_model = Bird3D(bird_collider)

camera.parent = bird_collider
camera.position = (0.35, 0.35, 0.05)
camera.rotation = (0, 0, 0)
camera.fov = 92

mouse.locked = True

crosshair = Entity(
    parent=camera.ui,
    model='quad',
    scale=(0.006, 0.006),
    color=color.rgba(255, 255, 255, 180),
    position=(0, 0)
)

wing_view_l = Entity(
    parent=camera,
    model='cube',
    color=C(255, 245, 140),
    scale=(0.04, 0.08, 0.35),
    position=(-0.35, -0.35, 0.5),
    rotation_y=20
)

wing_view_r = Entity(
    parent=camera,
    model='cube',
    color=C(255, 245, 140),
    scale=(0.04, 0.08, 0.35),
    position=(0.35, -0.35, 0.5),
    rotation_y=-20
)


class Ring:
    def __init__(self, z, y, x, radius=3.3, rtype="normal"):
        self.z = z
        self.y = y
        self.x = x
        self.radius = radius
        self.rtype = rtype
        self.scored = False
        self.parts = []

        self.move_phase = random.uniform(0, 6.28)

        self.move_speed = (
            random.uniform(0.8, 2.2)
            if rtype == "moving"
            else 0
        )

        self.base_y = y
        self.base_x = x

        segs = 24 if rtype != "bonus" else 16

        if rtype == "normal":
            col = C(0, 255, 200)
        elif rtype == "moving":
            col = C(255, 80, 80)
        elif rtype == "bonus":
            col = C(255, 215, 0)
        else:
            col = C(255, 0, 100)

        if rtype == "boss":
            radius *= 1.6

        self.col = col
        self.radius = radius

        for i in range(segs):
            ang = i / segs * 360

            px = (
                x +
                math.cos(math.radians(ang)) *
                radius
            )

            py = (
                y +
                math.sin(math.radians(ang)) *
                radius
            )

            e = Entity(
                model='cube',
                scale=(0.32, 0.32, 0.55),
                position=(px, py, z),
                color=col,
                collider=None
            )

            self.parts.append(e)

    def update(self):
        if self.rtype == "moving":
            self.move_phase += (
                time.dt *
                self.move_speed
            )

            self.y = (
                self.base_y +
                math.sin(self.move_phase) * 2.2
            )

            self.x = (
                self.base_x +
                math.cos(self.move_phase * 0.7) * 2.5
            )

            for i, e in enumerate(self.parts):
                segs = len(self.parts)

                ang = i / segs * 360

                e.x = (
                    self.x +
                    math.cos(math.radians(ang)) *
                    self.radius
                )

                e.y = (
                    self.y +
                    math.sin(math.radians(ang)) *
                    self.radius
                )

    def check(self, bpos):
        dz = abs(bpos.z - self.z)

        if dz < 1.5:
            dx = bpos.x - self.x
            dy = bpos.y - self.y

            dist = math.sqrt(
                dx * dx +
                dy * dy
            )

            if dist < self.radius - 0.6:
                return "score"

            if dist < self.radius + 0.7:
                return "hit"

        return None

    def destroy(self):
        for p in self.parts:
            destroy(p)


rings = []


def spawn_ring(z_off=38):
    z = bird_collider.z + z_off

    y = random.uniform(2.5, 13)
    x = random.uniform(-6, 6)

    rtype_roll = random.random()

    if score > 15 and rtype_roll < 0.18:
        rtype = "moving"
    elif rtype_roll < 0.12:
        rtype = "bonus"
    elif score > 30 and score % 12 == 0:
        rtype = "boss"
    else:
        rtype = "normal"

    if rtype == "normal":
        radius = max(
            1.9,
            3.6 - level * 0.12
        )
    elif rtype == "bonus":
        radius = 1.6
    elif rtype == "moving":
        radius = 3.0
    else:
        radius = 4.5

    rings.append(
        Ring(
            z,
            y,
            x,
            radius,
            rtype
        )
    )


for i in range(7):
    spawn_ring(18 + i * 12)


score_text = Text(
    text="RINGS: 0",
    position=(-0.82, 0.46),
    scale=1.4,
    color=color.white
)

best_text = Text(
    text="BEST: 0",
    position=(-0.82, 0.41),
    scale=1,
    color=color.white
)

combo_text = Text(
    text="",
    position=(0, 0.32),
    origin=(0, 0),
    scale=1.2,
    color=color.yellow
)

center_text = Text(
    text="",
    origin=(0, 0),
    scale=2,
    color=color.yellow
)

power_text = Text(
    text="",
    position=(-0.82, 0.36),
    scale=0.9,
    color=C(0, 255, 200)
)

instr = Text(
    text="LMB: Flap | R: Restart | ESC: Exit",
    position=(-0.82, -0.46),
    scale=0.70,
    color=color.white
)


def die():
    global game_over
    global best

    if game_over:
        return

    game_over = True

    bird_collider.vel_y = 0
    bird_collider.vel_x = 0

    burst(
        bird_collider.position,
        C(255, 220, 0),
        n=30,
        spread=3
    )

    burst(
        bird_collider.position,
        C(255, 80, 0),
        n=20,
        spread=2
    )

    center_text.text = (
        "YOU CRASHED!\n"
        f"RINGS: {score}\n"
        "R = RESTART"
    )

    if score > best:
        best = score
        best_text.text = f"BEST: {best}"


def restart():
    global score
    global game_over
    global rings
    global forward_speed
    global level
    global combo
    global powerup_active
    global powerup_timer

    for r in rings:
        r.destroy()

    rings.clear()

    score = 0
    level = 1
    combo = 0

    powerup_active = None
    powerup_timer = 0

    game_over = False

    bird_collider.position = (0, 7, 0)

    bird_collider.vel_y = 0
    bird_collider.vel_x = 0

    center_text.text = ""

    score_text.text = "RINGS: 0"
    best_text.text = f"BEST: {best}"

    forward_speed = base_speed

    for i in range(7):
        spawn_ring(18 + i * 12)


def apply_powerup(rtype):
    global powerup_active
    global powerup_timer

    if rtype == "bonus":
        powerup_active = "BONUS x3"
        powerup_timer = 5

        burst(
            bird_collider.position,
            C(255, 215, 0),
            n=20
        )

    elif rtype == "boss":
        powerup_active = "BOSS RING!"
        powerup_timer = 3


def update():
    global score
    global level
    global forward_speed
    global powerup_timer
    global powerup_active
    global combo

    if game_over:
        return

    bird_collider.vel_y += (
        gravity *
        time.dt
    )

    mouse_sens_x = 22
    mouse_sens_y = 16

    mx = 0
    my = 0

    try:
        mx = mouse.velocity.x
        my = mouse.velocity.y
    except:
        pass

    bird_collider.vel_x = lerp(
        bird_collider.vel_x,
        mx * mouse_sens_x,
        time.dt * 6
    )

    bird_collider.vel_y -= (
        my *
        mouse_sens_y
    )

    bird_collider.vel_x = max(
        -14,
        min(
            14,
            bird_collider.vel_x
        )
    )

    bird_collider.vel_y = max(
        -14,
        min(
            14,
            bird_collider.vel_y
        )
    )

    bird_collider.y += (
        bird_collider.vel_y *
        time.dt
    )

    bird_collider.x += (
        bird_collider.vel_x *
        time.dt
    )

    bird_collider.x = max(
        -11,
        min(
            11,
            bird_collider.x
        )
    )

    bird_collider.z += (
        forward_speed *
        time.dt
    )

    forward_speed += (
        time.dt *
        0.09
    )

    bird_bottom = (
        bird_collider.y -
        0.275
    )

    bird_top = (
        bird_collider.y +
        0.275
    )

    if bird_bottom <= FLOOR_Y:
        die()
        return

    if bird_top >= CEILING_Y:
        die()
        return

    new_level = 1 + score // 8

    if new_level != level:
        level = new_level

        burst(
            bird_collider.position,
            C(80, 220, 255),
            n=18
        )

        Sky().color = color.rgb(
            15 + level * 3,
            20 + level * 2,
            50 + level * 4
        )

    if powerup_timer > 0:
        powerup_timer -= time.dt

        power_text.text = (
            f"{powerup_active} "
            f"{int(powerup_timer)}s"
        )

        if powerup_timer <= 0:
            powerup_active = None
            power_text.text = ""

    for ring in rings[:]:

        ring.update()

        result = ring.check(
            bird_collider.position
        )

        if result == "score" and not ring.scored:

            ring.scored = True

            if ring.rtype == "bonus":
                add = 3
            elif ring.rtype == "boss":
                add = 5
            else:
                add = 1

            if powerup_active == "BONUS x3":
                add *= 3

            score += add
            combo += 1

            score_text.text = f"RINGS: {score}"

            if combo >= 3:
                combo_text.text = f"COMBO x{combo}!"

                invoke(
                    lambda: setattr(
                        combo_text,
                        'text',
                        ''
                    ),
                    delay=0.6
                )

            burst(
                Vec3(
                    ring.x,
                    ring.y,
                    ring.z
                ),
                ring.col,
                n=14,
                spread=1.5
            )

            if ring.rtype in ("bonus", "boss"):
                apply_powerup(ring.rtype)

            camera.fov = 105

            invoke(
                lambda: setattr(
                    camera,
                    'fov',
                    92
                ),
                delay=0.14
            )

        elif result == "hit":
            die()
            return

        if ring.z < bird_collider.z - 25:
            ring.destroy()
            rings.remove(ring)
            spawn_ring(50)

    bird_model.update_flap(
        bird_collider.vel_y
    )

    camera.rotation_x = lerp(
        camera.rotation_x,
        -bird_collider.vel_y * 1.2,
        time.dt * 4
    )

    camera.rotation_z = lerp(
        camera.rotation_z,
        -bird_collider.vel_x * 2,
        time.dt * 4
    )


def input(key):

    if key == 'left mouse down':

        if not game_over:

            bird_collider.vel_y = flap_force

            bird_model.flap_anim()

            burst(
                bird_collider.position +
                Vec3(-0.6, 0, 0),
                C(255, 255, 200),
                n=6,
                spread=0.8
            )

            wing_view_l.rotation_z = 30
            wing_view_r.rotation_z = -30

            invoke(
                lambda: setattr(
                    wing_view_l,
                    'rotation_z',
                    0
                ),
                delay=0.12
            )

            invoke(
                lambda: setattr(
                    wing_view_r,
                    'rotation_z',
                    0
                ),
                delay=0.12
            )

    if key == 'r':
        restart()

    if key == 'escape':
        application.quit()


app.run()
