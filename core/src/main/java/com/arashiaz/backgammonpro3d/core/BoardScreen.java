package com.arashiaz.backgammonpro3d.core;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.environment.*;
import com.badlogic.gdx.graphics.g3d.utils.*;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;

/**
 * Backgammon Pro 3D - polished board prototype.
 *
 * The scene is intentionally procedural so the Android build has no external
 * asset dependency yet. Geometry is kept reusable to reduce draw calls and
 * memory pressure on mobile GPUs.
 */
public final class BoardScreen extends ScreenAdapter implements InputProcessor {
    private static final float[] XS = {-7.05f, -5.75f, -4.45f, -3.15f, -1.85f, -0.55f,
                                       0.55f,  1.85f,  3.15f,  4.45f,  5.75f,  7.05f};
    private final PerspectiveCamera camera = new PerspectiveCamera(38f, 1f, 1f);
    private final ModelBatch batch = new ModelBatch();
    private final Environment environment = new Environment();
    private final Array<ModelInstance> models = new Array<>();
    private final Array<ModelInstance> gameObjects = new Array<>();
    private final Array<ModelInstance> moveMarkers = new Array<>();

    // Real backgammon state: positive = Light, negative = Dark.
    private final int[] points = new int[24];
    private int lightBar, darkBar;
    private int lightOff, darkOff;
    private boolean lightTurn = true;
    private final int[] dice = {0, 0};
    private final boolean[] dieUsed = {true, true};
    private boolean diceRolled;
    private int selectedPoint = -1;

    private final ShapeRenderer uiShape = new ShapeRenderer();
    private final SpriteBatch uiBatch = new SpriteBatch();
    private final BitmapFont uiFont = new BitmapFont();
    private final GlyphLayout uiLayout = new GlyphLayout();
    private final Rectangle rollButton = new Rectangle();
    private boolean uiTouch;
    private float downX, downY;
    private boolean dragged;
    private boolean diceTouch;
    private float diceThrowStrength;
    private float diceSwipeDistance;
    private String status = "Roll the dice to start";
    private float statusTimer;
    private final ModelBuilder mb = new ModelBuilder();

    private Model baseModel, playingSurfaceModel, railModel, barModel;
    private Model darkPointModel, lightPointModel;
    private Model darkChecker, lightChecker;
    private Model diceModel, dieDotModel;
    private Model accentModel;

    private float lastX, lastY;
    private boolean dragging;
    private float cameraAzimuth = 0f;
    private float cameraElevation = 58f;
    private float cameraDistance = 23.5f;

    private final Vector3 cameraTarget = new Vector3(0f, 0.25f, 0f);
    private final Vector3 tmp = new Vector3();
    private ModelInstance dieInstanceA, dieInstanceB;
    private float diceRollTime;
    private float diceRollElapsed;
    private float lastDiceLiftA;
    private float lastDiceLiftB;
    private int rollingFaceA = 1;
    private int rollingFaceB = 1;
    private ModelInstance movingPiece;
    private final Vector3 moveStart = new Vector3();
    private final Vector3 moveEnd = new Vector3();
    private float moveTime;
    private static final float MOVE_DURATION = 0.34f;
    private boolean moveAnimating;

    @Override
    public void show() {
        Gdx.input.setInputProcessor(this);
        uiFont.getData().setScale(1.12f);
        resetGameState();

        environment.set(new ColorAttribute(
                ColorAttribute.AmbientLight, 0.42f, 0.43f, 0.46f, 1f));
        environment.add(new DirectionalLight().set(
                1.0f, 0.91f, 0.78f, -0.55f, -1.0f, -0.35f));
        environment.add(new DirectionalLight().set(
                0.28f, 0.34f, 0.48f, 0.55f, -0.45f, 0.65f));

        buildBoard();
        updateCamera();
    }


    private void resetGameState() {
        for (int i = 0; i < 24; i++) points[i] = 0;
        points[0] = 2; points[5] = 5; points[7] = 3; points[11] = 5;
        points[23] = -2; points[18] = -5; points[16] = -3; points[12] = -5;
        lightBar = darkBar = lightOff = darkOff = 0;
        lightTurn = true;
        dice[0] = dice[1] = 0;
        dieUsed[0] = dieUsed[1] = true;
        diceRolled = false;
        selectedPoint = -1;
        status = "Roll the dice to start";
        statusTimer = 0f;
    }

    private boolean allDiceUsed() { return dieUsed[0] && dieUsed[1]; }

    private boolean owns(int point) {
        return point >= 0 && point < 24 && (lightTurn ? points[point] > 0 : points[point] < 0);
    }

    private int moveDistance(int from, int to) {
        return lightTurn ? to - from : from - to;
    }

    private boolean openPoint(int to) {
        if (to < 0 || to >= 24) return false;
        return lightTurn ? points[to] >= -1 : points[to] <= 1;
    }

    private boolean canMoveWithDie(int from, int to, int die) {
        return owns(from) && openPoint(to) && moveDistance(from, to) == die;
    }

    private boolean canUseDie(int from, int die) {
        int to = lightTurn ? from + die : from - die;
        return canMoveWithDie(from, to, die);
    }

    private boolean hasAnyMove() {
        for (int p = 0; p < 24; p++) {
            if (!owns(p)) continue;
            for (int d = 0; d < 2; d++) {
                if (!dieUsed[d] && canUseDie(p, dice[d])) return true;
            }
        }
        return false;
    }

    private boolean hitDie(int screenX, int screenY) {
        Ray ray = camera.getPickRay(screenX, screenY);
        Plane plane = new Plane(Vector3.Y, 1.06f);
        if (!Intersector.intersectRayPlane(ray, plane, tmp)) return false;
        return (tmp.x + 1.55f) * (tmp.x + 1.55f) + tmp.z * tmp.z < 1.15f * 1.15f
                || (tmp.x - 1.55f) * (tmp.x - 1.55f) + tmp.z * tmp.z < 1.15f * 1.15f;
    }

    private void rollDice() {
        rollDice(0f);
    }

    private void rollDice(float throwStrength) {
        if (diceRolled && !allDiceUsed()) {
            status = "Use the current dice first";
            statusTimer = 1.1f;
            return;
        }
        dice[0] = MathUtils.random(1, 6);
        dice[1] = MathUtils.random(1, 6);
        rollingFaceA = MathUtils.random(1, 6);
        rollingFaceB = MathUtils.random(1, 6);
        dieUsed[0] = dieUsed[1] = false;
        diceRolled = true;
        diceRollElapsed = 0f;
        lastDiceLiftA = 0f;
        lastDiceLiftB = 0f;
        diceThrowStrength = MathUtils.clamp(throwStrength, 0f, 650f);
        diceRollTime = 0.85f;
        selectedPoint = -1;
        clearMoveMarkers();
        status = lightTurn ? "Light: choose a checker" : "Dark: choose a checker";
        statusTimer = 1.5f;
        rebuildGameObjects();
    }

    private void rebuildGameObjects() {
        for (ModelInstance instance : gameObjects) models.removeValue(instance, true);
        gameObjects.clear();

        addStateStacks();

        dieInstanceA = new ModelInstance(diceModel, -1.55f, 1.06f, 0f);
        dieInstanceB = new ModelInstance(diceModel,  1.55f, 1.06f, 0f);
        dieInstanceA.transform.rotate(Vector3.Y, -9f);
        dieInstanceB.transform.rotate(Vector3.Y, 12f);
        gameObjects.add(dieInstanceA); models.add(dieInstanceA);
        gameObjects.add(dieInstanceB); models.add(dieInstanceB);
        // During the roll the cubes are intentionally clean: pips are added
        // only after the final face is settled, so they never float while the
        // cube spins. The final face is rebuilt atomically when the animation ends.
        if (diceRollTime <= 0f) {
            if (dice[0] > 0) addTopPips(-1.55f, 1.72f, 0f, dice[0]);
            if (dice[1] > 0) addTopPips( 1.55f, 1.72f, 0f, dice[1]);
        }
    }

    private void addStateStacks() {
        for (int p = 0; p < 24; p++) {
            int count = Math.abs(points[p]);
            if (count == 0) continue;
            Model model = points[p] > 0 ? lightChecker : darkChecker;
            for (int i = 0; i < count; i++) {
                int col = p < 12 ? p : 23 - p;
                float x = XS[col];
                float z = p < 12 ? -3.30f + i * 0.43f : 3.30f - i * 0.43f;
                ModelInstance piece = new ModelInstance(model, x, 0.78f + i * 0.425f, z);
                gameObjects.add(piece); models.add(piece);
            }
        }
        for (int i = 0; i < lightBar; i++) {
            ModelInstance piece = new ModelInstance(lightChecker, -0.95f, 0.78f + i * 0.43f, 0f);
            gameObjects.add(piece); models.add(piece);
        }
        for (int i = 0; i < darkBar; i++) {
            ModelInstance piece = new ModelInstance(darkChecker, 0.95f, 0.78f + i * 0.43f, 0f);
            gameObjects.add(piece); models.add(piece);
        }
    }

    private ModelInstance findTopChecker(int point) {
        int count = Math.abs(points[point]);
        if (count <= 0) return null;
        int col = point < 12 ? point : 23 - point;
        float x = XS[col];
        float z = point < 12 ? -3.30f + (count - 1) * 0.43f : 3.30f - (count - 1) * 0.43f;
        Model expected = points[point] > 0 ? lightChecker : darkChecker;
        for (ModelInstance instance : gameObjects) {
            if (instance.model != expected) continue;
            instance.transform.getTranslation(tmp);
            if (Math.abs(tmp.x - x) < 0.08f && Math.abs(tmp.z - z) < 0.08f) return instance;
        }
        return null;
    }

    private Vector3 pointPosition(int point, int stackIndex) {
        int col = point < 12 ? point : 23 - point;
        float x = XS[col];
        float z = point < 12 ? -3.30f + stackIndex * 0.43f : 3.30f - stackIndex * 0.43f;
        return new Vector3(x, 0.78f + stackIndex * 0.425f, z);
    }

    private void startMoveAnimation(ModelInstance piece, int from, int destination, int sourceStackIndex) {
        if (piece == null) {
            rebuildGameObjects();
            finishMoveState();
            return;
        }
        moveStart.set(pointPosition(from, sourceStackIndex));
        int destinationCountBefore = Math.abs(points[destination]);
        int destinationStackIndex = (points[destination] != 0 && ((points[destination] > 0) == lightTurn))
                ? destinationCountBefore - 1 : destinationCountBefore;
        moveEnd.set(pointPosition(destination, Math.max(0, destinationStackIndex)));
        moveEnd.y = 0.82f + Math.max(0, destinationStackIndex) * 0.425f;

        models.removeValue(piece, true);
        gameObjects.removeValue(piece, true);
        movingPiece = piece;
        movingPiece.transform.setToTranslation(moveStart);
        models.add(movingPiece);
        moveTime = 0f;
        moveAnimating = true;
    }

    private void finishMoveState() {
        rebuildGameObjects();
        if (allDiceUsed() || !hasAnyMove()) {
            lightTurn = !lightTurn;
            diceRolled = false;
            dieUsed[0] = dieUsed[1] = true;
            status = lightTurn ? "Light's turn — roll" : "Dark's turn — roll";
        } else {
            status = lightTurn ? "Light: choose your next move" : "Dark: choose your next move";
        }
        statusTimer = 1.1f;
    }

    private void selectPoint(int point) {
        if (!diceRolled || allDiceUsed()) return;
        if (!owns(point)) {
            status = "Select your checker";
            statusTimer = 0.9f;
            return;
        }
        boolean movable = false;
        for (int d = 0; d < 2; d++) if (!dieUsed[d] && canUseDie(point, dice[d])) movable = true;
        if (!movable) {
            status = "No legal move for this checker";
            statusTimer = 0.9f;
            return;
        }
        selectedPoint = selectedPoint == point ? -1 : point;
        rebuildMoveMarkers();
    }

    private void rebuildMoveMarkers() {
        clearMoveMarkers();
        if (selectedPoint < 0) return;
        for (int d = 0; d < 2; d++) {
            if (dieUsed[d]) continue;
            int to = lightTurn ? selectedPoint + dice[d] : selectedPoint - dice[d];
            if (canMoveWithDie(selectedPoint, to, dice[d])) {
                int col = to < 12 ? to : 23 - to;
                float z = to < 12 ? -3.25f : 3.25f;
                ModelInstance marker = new ModelInstance(accentModel, XS[col], 0.68f, z);
                moveMarkers.add(marker);
            }
        }
    }

    private void clearMoveMarkers() { moveMarkers.clear(); }

    private void tryMove(int destination) {
        if (moveAnimating || diceRollTime > 0f) return;
        if (selectedPoint < 0) {
            selectPoint(destination);
            return;
        }

        int dieIndex = -1;
        for (int d = 0; d < 2; d++) {
            if (!dieUsed[d] && canMoveWithDie(selectedPoint, destination, dice[d])) {
                dieIndex = d;
                break;
            }
        }
        if (dieIndex < 0) {
            if (owns(destination)) selectPoint(destination);
            else {
                status = "That move is not allowed";
                statusTimer = 0.9f;
            }
            return;
        }

        final int from = selectedPoint;
        final int sourceStackIndex = Math.abs(points[from]) - 1;
        ModelInstance piece = findTopChecker(from);

        int sign = lightTurn ? 1 : -1;
        if (lightTurn && points[destination] == -1) { points[destination] = 0; darkBar++; }
        if (!lightTurn && points[destination] == 1) { points[destination] = 0; lightBar++; }
        points[from] -= sign;
        points[destination] += sign;
        dieUsed[dieIndex] = true;
        selectedPoint = -1;
        clearMoveMarkers();

        status = "Moving...";
        statusTimer = 0.8f;
        startMoveAnimation(piece, from, destination, sourceStackIndex);
    }

    private int nearestPoint(float x, float z) {
        int best = -1;
        float bestDistance = 1.05f;
        for (int p = 0; p < 24; p++) {
            int col = p < 12 ? p : 23 - p;
            float px = XS[col];
            float pz = p < 12 ? -3.25f : 3.25f;
            float distance = Vector2.dst(x, z, px, pz);
            if (distance < bestDistance) { bestDistance = distance; best = p; }
        }
        return best;
    }

    private void pickBoard(int screenX, int screenY) {
        if (moveAnimating) return;
        Ray ray = camera.getPickRay(screenX, screenY);
        Plane plane = new Plane(Vector3.Y, 0.68f);
        if (Intersector.intersectRayPlane(ray, plane, tmp)) {
            int point = nearestPoint(tmp.x, tmp.z);
            if (point >= 0) tryMove(point);
        }
    }

    private Material wood(float r, float g, float b, float shine) {
        Material m = new Material(ColorAttribute.createDiffuse(r, g, b, 1f));
        m.set(ColorAttribute.createSpecular(
                Math.min(1f, r + 0.18f),
                Math.min(1f, g + 0.18f),
                Math.min(1f, b + 0.18f), 1f));
        m.set(FloatAttribute.createShininess(shine));
        return m;
    }

    private Material surface(float r, float g, float b) {
        Material m = new Material(ColorAttribute.createDiffuse(r, g, b, 1f));
        m.set(ColorAttribute.createSpecular(0.12f, 0.10f, 0.08f, 1f));
        m.set(FloatAttribute.createShininess(10f));
        return m;
    }

    private void buildBoard() {
        final long attrs = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;

        // Layered wooden frame: darker body + inset + thin highlight rail.
        baseModel = mb.createBox(
                18.6f, 0.72f, 10.7f,
                wood(0.19f, 0.075f, 0.028f, 18f), attrs);
        models.add(new ModelInstance(baseModel, 0f, -0.42f, 0f));

        playingSurfaceModel = mb.createBox(
                17.85f, 0.34f, 9.95f,
                wood(0.34f, 0.13f, 0.045f, 16f), attrs);
        models.add(new ModelInstance(playingSurfaceModel, 0f, 0.02f, 0f));

        // Warm cloth/felt inset.
        Model felt = mb.createBox(
                16.95f, 0.18f, 9.05f,
                surface(0.44f, 0.22f, 0.075f), attrs);
        models.add(new ModelInstance(felt, 0f, 0.27f, 0f));

        railModel = mb.createBox(
                17.45f, 0.10f, 9.55f,
                wood(0.62f, 0.27f, 0.075f, 28f), attrs);
        models.add(new ModelInstance(railModel, 0f, 0.35f, 0f));

        Model innerMat = mb.createBox(
                16.95f, 0.08f, 9.05f,
                surface(0.46f, 0.23f, 0.075f), attrs);
        models.add(new ModelInstance(innerMat, 0f, 0.405f, 0f));

        // Central bar with a subtle raised center strip.
        barModel = mb.createBox(
                0.72f, 0.22f, 8.95f,
                wood(0.22f, 0.085f, 0.028f, 22f), attrs);
        models.add(new ModelInstance(barModel, 0f, 0.48f, 0f));

        Model barHighlight = mb.createBox(
                0.16f, 0.045f, 8.55f,
                wood(0.62f, 0.29f, 0.075f, 30f), attrs);
        models.add(new ModelInstance(barHighlight, 0f, 0.61f, 0f));

        // Real flat triangular points, not cones.
        darkPointModel = createPointModel(
                wood(0.17f, 0.045f, 0.025f, 20f), attrs, true);
        lightPointModel = createPointModel(
                wood(0.76f, 0.54f, 0.25f, 24f), attrs, false);

        float[] xs = XS;

        for (int i = 0; i < xs.length; i++) {
            boolean dark = (i % 2 == 0);
            Model point = dark ? darkPointModel : lightPointModel;

            ModelInstance top = new ModelInstance(point, xs[i], 0.66f, 0f);
            top.transform.translate(0f, 0f, 2.42f);
            models.add(top);

            ModelInstance bottom = new ModelInstance(point, xs[i], 0.66f, 0f);
            bottom.transform.translate(0f, 0f, -2.42f);
            bottom.transform.rotate(Vector3.Y, 180f);
            models.add(bottom);
        }

        // Shared checker meshes. High radial resolution keeps the circular
        // silhouette clean on modern phone displays while remaining lightweight.
        darkChecker = createBeveledCheckerModel(
                new Material(
                        ColorAttribute.createDiffuse(0.035f, 0.032f, 0.040f, 1f),
                        ColorAttribute.createSpecular(0.34f, 0.34f, 0.39f, 1f),
                        FloatAttribute.createShininess(55f)),
                attrs);

        lightChecker = createBeveledCheckerModel(
                new Material(
                        ColorAttribute.createDiffuse(0.88f, 0.78f, 0.57f, 1f),
                        ColorAttribute.createSpecular(0.55f, 0.49f, 0.38f, 1f),
                        FloatAttribute.createShininess(48f)),
                attrs);

        diceModel = mb.createBox(
                1.22f, 1.22f, 1.22f,
                new Material(
                        ColorAttribute.createDiffuse(0.94f, 0.92f, 0.84f, 1f),
                        ColorAttribute.createSpecular(0.72f, 0.68f, 0.58f, 1f),
                        FloatAttribute.createShininess(70f)),
                attrs);

        dieDotModel = mb.createCylinder(
                0.18f, 0.035f, 0.18f, 24,
                new Material(
                        ColorAttribute.createDiffuse(0.035f, 0.032f, 0.028f, 1f),
                        ColorAttribute.createSpecular(0.12f, 0.12f, 0.12f, 1f),
                        FloatAttribute.createShininess(20f)),
                attrs);

        // Small gold center emblem/trim.
        accentModel = mb.createCylinder(
                0.16f, 0.035f, 0.16f, 32,
                wood(0.78f, 0.50f, 0.17f, 52f), attrs);
        models.add(new ModelInstance(accentModel, 0f, 0.68f, 0f));
        rebuildGameObjects();
    }

    private Model createBeveledCheckerModel(Material material, long attrs) {
        mb.begin();
        MeshPartBuilder p = mb.part("checker", GL20.GL_TRIANGLES, attrs, material);

        final int segments = 64;
        final float radius = 0.50f;
        final float bevelRadius = 0.055f;
        final float half = 0.21f;
        final float bevelY = 0.26f;

        for (int i = 0; i < segments; i++) {
            float a0 = MathUtils.PI2 * i / segments;
            float a1 = MathUtils.PI2 * (i + 1) / segments;
            float x0 = MathUtils.cos(a0) * radius;
            float z0 = MathUtils.sin(a0) * radius;
            float x1 = MathUtils.cos(a1) * radius;
            float z1 = MathUtils.sin(a1) * radius;

            float bx0 = MathUtils.cos(a0) * (radius - bevelRadius);
            float bz0 = MathUtils.sin(a0) * (radius - bevelRadius);
            float bx1 = MathUtils.cos(a1) * (radius - bevelRadius);
            float bz1 = MathUtils.sin(a1) * (radius - bevelRadius);

            Vector3 top0 = new Vector3(bx0, half + bevelY, bz0);
            Vector3 top1 = new Vector3(bx1, half + bevelY, bz1);
            Vector3 topC0 = new Vector3(x0, half, z0);
            Vector3 topC1 = new Vector3(x1, half, z1);
            Vector3 bot0 = new Vector3(bx0, -half - bevelY, bz0);
            Vector3 bot1 = new Vector3(bx1, -half - bevelY, bz1);
            Vector3 botC0 = new Vector3(x0, -half, z0);
            Vector3 botC1 = new Vector3(x1, -half, z1);

            p.triangle(top0, topC0, topC1);
            p.triangle(top0, topC1, top1);
            p.triangle(bot0, bot1, botC1);
            p.triangle(bot0, botC1, botC0);
            p.triangle(new Vector3(0f, half + bevelY, 0f), top1, top0);
            p.triangle(new Vector3(0f, -half - bevelY, 0f), bot0, bot1);
            p.triangle(topC0, botC0, botC1);
            p.triangle(topC0, botC1, topC1);
            p.triangle(top0, top1, bot1);
            p.triangle(top0, bot1, bot0);
        }
        return mb.end();
    }

    private Model createPointModel(Material material, long attrs, boolean unused) {
        mb.begin();
        MeshPartBuilder p = mb.part("point", GL20.GL_TRIANGLES, attrs, material);

        final float w = 0.47f;
        final float y0 = 0f;
        final float y1 = 0.12f;
        final float zBase = 2.02f;
        final float zTip = -1.92f;

        Vector3 a0 = new Vector3(-w, y0, zBase);
        Vector3 b0 = new Vector3( w, y0, zBase);
        Vector3 c0 = new Vector3(0f, y0, zTip);
        Vector3 a1 = new Vector3(-w, y1, zBase);
        Vector3 b1 = new Vector3( w, y1, zBase);
        Vector3 c1 = new Vector3(0f, y1, zTip);

        p.triangle(a1, b1, c1);
        p.triangle(c0, b0, a0);
        p.triangle(a0, b0, b1);
        p.triangle(a0, b1, a1);
        p.triangle(b0, c0, c1);
        p.triangle(b0, c1, b1);
        p.triangle(c0, a0, a1);
        p.triangle(c0, a1, c1);

        return mb.end();
    }

    private void addStack(Model model, float x, float z, int count) {
        for (int i = 0; i < count; i++) {
            float offset = i * 0.43f;
            float signed = z > 0f ? -offset : offset;
            models.add(new ModelInstance(model, x, 0.78f + i * 0.425f, z + signed));
        }
    }

    private void addTopPips(float x, float y, float z, int number) {
        float d = 0.30f;
        if (number == 1 || number == 3 || number == 5) addPip(x, y, z);
        if (number >= 2) {
            addPip(x - d, y, z - d);
            addPip(x + d, y, z + d);
        }
        if (number >= 4) {
            addPip(x - d, y, z + d);
            addPip(x + d, y, z - d);
        }
        if (number == 6) {
            addPip(x - d, y, z);
            addPip(x + d, y, z);
        }
    }

    private void addPip(float x, float y, float z) {
        ModelInstance pip = new ModelInstance(dieDotModel, x, y, z);
        gameObjects.add(pip);
        models.add(pip);
    }

    private void updateCamera() {
        float az = MathUtils.degreesToRadians * cameraAzimuth;
        float el = MathUtils.degreesToRadians * cameraElevation;
        float cosEl = MathUtils.cos(el);

        camera.position.set(
                cameraTarget.x + cameraDistance * cosEl * MathUtils.sin(az),
                cameraTarget.y + cameraDistance * MathUtils.sin(el),
                cameraTarget.z + cameraDistance * cosEl * MathUtils.cos(az));

        camera.lookAt(cameraTarget);
        camera.up.set(Vector3.Y);
        camera.near = 0.1f;
        camera.far = 100f;
        camera.update();
    }

    @Override
    public void render(float delta) {
        if (statusTimer > 0f) statusTimer -= delta;
        if (diceRollTime > 0f) {
            diceRollElapsed += delta;
            diceRollTime = Math.max(0f, diceRollTime - delta);
            float spin = (1080f + diceThrowStrength * 2.2f) * delta;
            if (dieInstanceA != null) {
                dieInstanceA.transform.rotate(Vector3.X, spin).rotate(Vector3.Y, spin * 0.65f);
                float liftA = MathUtils.sin(diceRollElapsed * 24f) * 0.22f;
                dieInstanceA.transform.translate(0f, liftA - lastDiceLiftA, 0f);
                lastDiceLiftA = liftA;
            }
            if (dieInstanceB != null) {
                dieInstanceB.transform.rotate(Vector3.X, -spin * 0.85f).rotate(Vector3.Z, spin);
                float liftB = MathUtils.sin(diceRollElapsed * 27f + 0.8f) * 0.22f;
                dieInstanceB.transform.translate(0f, liftB - lastDiceLiftB, 0f);
                lastDiceLiftB = liftB;
            }
            if (diceRollTime <= 0f) {
                diceRollElapsed = 0f;
                lastDiceLiftA = 0f;
                lastDiceLiftB = 0f;
                rebuildGameObjects();
                if (Gdx.input.isPeripheralAvailable(Input.Peripheral.Vibrator)) {
                    try {
                        Gdx.input.vibrate(35);
                    } catch (SecurityException ignored) {
                        // Haptics are optional and must never crash the game.
                    }
                }
            }
        }
        if (moveAnimating && movingPiece != null) {
            moveTime += delta;
            float t = MathUtils.clamp(moveTime / MOVE_DURATION, 0f, 1f);
            float eased = t * t * (3f - 2f * t);
            float arc = MathUtils.sin(MathUtils.PI * t) * 1.15f;
            tmp.set(moveStart).lerp(moveEnd, eased);
            tmp.y += arc;
            movingPiece.transform.setToTranslation(tmp);
            if (t >= 1f) {
                models.removeValue(movingPiece, true);
                movingPiece = null;
                moveAnimating = false;
                finishMoveState();
            }
        }

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.012f, 0.015f, 0.020f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        camera.update();
        batch.begin(camera);
        for (ModelInstance model : models) batch.render(model, environment);
        for (ModelInstance marker : moveMarkers) batch.render(marker, environment);
        batch.end();

        renderUi();
    }

    private void renderUi() {
        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();
        rollButton.set(24f, 24f, Math.min(260f, w * 0.30f), 84f);

        uiShape.begin(ShapeRenderer.ShapeType.Filled);
        uiShape.setColor(0.018f, 0.023f, 0.032f, 0.96f);
        uiShape.rect(0f, h - 112f, w, 112f);
        uiShape.end();

        uiBatch.begin();
        uiFont.getData().setScale(1.28f);
        uiFont.setColor(Color.WHITE);
        String turn = lightTurn ? "LIGHT" : "DARK";
        String diceText = diceRolled
                ? (diceRollTime > 0f ? "ROLLING" : dice[0] + "  •  " + dice[1])
                : "READY";
        uiLayout.setText(uiFont, turn + "   " + diceText);
        uiFont.draw(uiBatch, uiLayout, 28f, h - 38f);

        uiFont.getData().setScale(1.0f);
        uiFont.setColor(0.90f, 0.84f, 0.66f, 1f);
        String score = "BAR  " + lightBar + " / " + darkBar + "     OFF  " + lightOff + " / " + darkOff;
        uiFont.draw(uiBatch, score, 28f, h - 78f);

        uiFont.getData().setScale(0.98f);
        if (statusTimer > 0f || !diceRolled) {
            uiFont.setColor(0.82f, 0.86f, 0.92f, 1f);
            String hint = !diceRolled ? "Tap or throw the dice" : status;
            uiFont.draw(uiBatch, hint, 28f, 42f);
        }
        uiFont.getData().setScale(1.12f);
        uiBatch.end();
    }

    @Override
    public boolean touchDown(int x, int y, int pointer, int button) {
        downX = lastX = x;
        downY = lastY = y;
        dragged = false;
        diceTouch = !moveAnimating && diceRollTime <= 0f && hitDie(x, y);
        diceSwipeDistance = 0f;
        uiTouch = false;
        return true;
    }

    @Override
    public boolean touchDragged(int x, int y, int pointer) {
        if (diceTouch) {
            float dx = x - downX;
            float dy = y - downY;
            diceSwipeDistance = MathUtils.clamp((float)Math.sqrt(dx * dx + dy * dy), 0f, 300f);
            if (diceSwipeDistance > 12f) dragged = true;
            lastX = x;
            lastY = y;
            return true;
        }
        if (Math.abs(x - downX) + Math.abs(y - downY) > 12f) dragged = true;
        if (!dragged) return true;

        float dx = x - lastX;
        float dy = y - lastY;
        cameraAzimuth = MathUtils.clamp(cameraAzimuth - dx * 0.13f, -22f, 22f);
        cameraElevation = MathUtils.clamp(cameraElevation - dy * 0.09f, 48f, 68f);
        updateCamera();
        lastX = x;
        lastY = y;
        return true;
    }

    @Override
    public boolean touchUp(int x, int y, int pointer, int button) {
        if (diceTouch) {
            if (!moveAnimating && diceRollTime <= 0f) {
                if (diceRolled && !allDiceUsed()) {
                    status = "Use the current dice first";
                    statusTimer = 1.0f;
                } else {
                    rollDice(diceSwipeDistance * 2.2f);
                }
            }
            diceTouch = false;
            dragged = false;
            return true;
        }
        if (!dragged) pickBoard(x, y);
        dragged = false;
        return true;
    }

    @Override
    public boolean touchCancelled(int x, int y, int pointer, int button) {
        dragged = false;
        uiTouch = false;
        return true;
    }

    @Override public boolean mouseMoved(int x, int y) { return false; }
    @Override public boolean keyTyped(char character) { return false; }
    @Override public boolean keyDown(int keycode) { return false; }
    @Override public boolean keyUp(int keycode) { return false; }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        cameraDistance = MathUtils.clamp(
                cameraDistance * (1f + amountY * 0.055f), 16f, 30f);
        updateCamera();
        return true;
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = Math.max(1, width);
        camera.viewportHeight = Math.max(1, height);
        camera.update();
    }

    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}

    @Override
    public void dispose() {
        batch.dispose();
        uiShape.dispose();
        uiBatch.dispose();
        uiFont.dispose();
        if (baseModel != null) baseModel.dispose();
        if (playingSurfaceModel != null) playingSurfaceModel.dispose();
        if (railModel != null) railModel.dispose();
        if (barModel != null) barModel.dispose();
        if (darkPointModel != null) darkPointModel.dispose();
        if (lightPointModel != null) lightPointModel.dispose();
        if (darkChecker != null) darkChecker.dispose();
        if (lightChecker != null) lightChecker.dispose();
        if (diceModel != null) diceModel.dispose();
        if (dieDotModel != null) dieDotModel.dispose();
        if (accentModel != null) accentModel.dispose();
    }
}
