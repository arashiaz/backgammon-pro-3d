package com.arashiaz.backgammonpro3d.core;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.environment.*;
import com.badlogic.gdx.graphics.g3d.utils.*;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo;
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
 * The board uses a real photographic walnut veneer texture for the furniture surfaces.
 * Geometry remains reusable to keep draw calls and mobile GPU memory under control.
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

    private Model floorModel, baseModel, playingSurfaceModel, railModel, barModel;
    private Model sideTrayModel, sideTrayInsetModel, hingePlateModel, medallionModel, medallionRingModel;
    private Model darkPointModel, lightPointModel;
    private Model darkChecker, lightChecker;
    private Model diceModel, dieDotModel;
    private Model accentModel;
    private Model diceTrayModel, screwModel;
    private Model diceEdgeModel;
    private Texture woodTexture;
    private Texture boardArtworkTexture;

    private float lastX, lastY;
    private boolean dragging;
    private float cameraAzimuth = 0f;
    private float cameraElevation = 68f;
    private float cameraDistance = 21.5f;

    private final Vector3 cameraTarget = new Vector3(0f, 0.25f, 0f);
    private final Vector3 tmp = new Vector3();
    private ModelInstance dieInstanceA, dieInstanceB;
    private float diceRollTime;
    private float diceRollElapsed;
    private float lastDiceLiftA;
    private float lastDiceLiftB;
    private float diceSpinA;
    private float diceSpinB;
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
                ColorAttribute.AmbientLight, 0.27f, 0.24f, 0.21f, 1f));
        environment.add(new DirectionalLight().set(
                1.05f, 0.92f, 0.72f, -0.55f, -1.0f, -0.35f));
        environment.add(new DirectionalLight().set(
                0.28f, 0.34f, 0.48f, 0.55f, -0.45f, 0.65f));
        // A focused warm key and a restrained cool rim give the wood and
        // checker bevels readable depth without requiring expensive shadows.
        environment.add(new DirectionalLight().set(
                0.50f, 0.34f, 0.18f, -0.25f, -0.75f, 0.82f));
        // A soft frontal fill keeps the dark checkers readable while preserving
        // the stronger warm key on the wood.
        environment.add(new DirectionalLight().set(
                0.20f, 0.22f, 0.28f, 0.10f, -0.55f, -0.92f));

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
        diceSpinA = MathUtils.random(0f, 360f);
        diceSpinB = MathUtils.random(0f, 360f);
        diceRollTime = 0.95f;
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
            if (dice[0] > 0) addTopPips(-1.55f, 1.690f, 0f, dice[0]);
            if (dice[1] > 0) addTopPips( 1.55f, 1.690f, 0f, dice[1]);
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

    private float smoothNoise(float x, float y) {
        int x0 = MathUtils.floor(x);
        int y0 = MathUtils.floor(y);
        float fx = x - x0;
        float fy = y - y0;
        fx = fx * fx * (3f - 2f * fx);
        fy = fy * fy * (3f - 2f * fy);

        float n00 = hashNoise(x0, y0);
        float n10 = hashNoise(x0 + 1, y0);
        float n01 = hashNoise(x0, y0 + 1);
        float n11 = hashNoise(x0 + 1, y0 + 1);

        float nx0 = MathUtils.lerp(n00, n10, fx);
        float nx1 = MathUtils.lerp(n01, n11, fx);
        return MathUtils.lerp(nx0, nx1, fy);
    }

    private float hashNoise(int x, int y) {
        int n = x * 374761393 + y * 668265263;
        n = (n ^ (n >>> 13)) * 1274126177;
        n ^= (n >>> 16);
        return (n & 0x7fffffff) / 2147483647f;
    }

    private Texture createNaturalWoodTexture(int size) {
        // Mobile-safe procedural walnut: irregular broad grain, soft pores and
        // warped growth lines. No periodic sine bands, so the board reads as
        // wood rather than a set of horizontal stripes.
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        for (int y = 0; y < size; y++) {
            float v = y / (float)(size - 1);
            for (int x = 0; x < size; x++) {
                float u = x / (float)(size - 1);

                float warpX = (smoothNoise(u * 2.2f, v * 2.2f) - 0.5f) * 1.15f;
                float warpY = (smoothNoise(u * 1.7f + 13.7f, v * 1.7f + 7.2f) - 0.5f) * 0.55f;

                float broad = smoothNoise(u * 3.0f + warpX, v * 3.0f + warpY);
                float medium = smoothNoise(u * 9.0f + warpX * 2.0f, v * 7.0f + warpY);
                float pore = smoothNoise(u * 28.0f + warpX * 3.0f, v * 22.0f + warpY * 2.0f);

                float grain = MathUtils.lerp(broad, medium, 0.38f);
                float value = 0.82f
                        + (grain - 0.5f) * 0.16f
                        + (pore - 0.5f) * 0.025f;

                // Gentle warm walnut variation rather than orange plastic.
                float r = MathUtils.clamp(0.37f * value, 0f, 1f);
                float g = MathUtils.clamp(0.205f * value, 0f, 1f);
                float b = MathUtils.clamp(0.105f * value, 0f, 1f);

                pm.setColor(r, g, b, 1f);
                pm.drawPixel(x, y);
            }
        }

        Texture t = new Texture(pm, true);
        t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        pm.dispose();
        return t;
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

    private Material woodTextured(float r, float g, float b, float shine) {
        // Deliberately texture-free: the previous procedural grain produced
        // distracting horizontal banding/moire on the mobile display.
        Material m = new Material(ColorAttribute.createDiffuse(r, g, b, 1f));
        m.set(ColorAttribute.createSpecular(
                Math.min(1f, r + 0.14f),
                Math.min(1f, g + 0.14f),
                Math.min(1f, b + 0.14f), 1f));
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
        final long attrs = VertexAttributes.Usage.Position
                | VertexAttributes.Usage.Normal
                | VertexAttributes.Usage.TextureCoordinates;

        // A dark furniture-like floor grounds the board in the scene instead
        // of leaving it floating against a flat black background.
        floorModel = mb.createBox(
                28.0f, 0.10f, 19.0f,
                surface(0.018f, 0.022f, 0.028f), attrs);
        models.add(new ModelInstance(floorModel, 0f, -0.84f, 0f));

        // Layered wooden frame: darker body + inset + thin highlight rail.
        baseModel = mb.createBox(
                21.4f, 0.72f, 10.7f,
                woodTextured(0.52f, 0.42f, 0.34f, 30f), attrs);
        models.add(new ModelInstance(baseModel, 0f, -0.42f, 0f));

        // Warm walnut base. The visible playfield is the top inner panel,
        // so the procedural grain must sit ABOVE the felt/innerMat layer,
        // not down at the hidden wooden base.
        playingSurfaceModel = mb.createBox(
                18.35f, 0.34f, 9.95f,
                new Material(
                        ColorAttribute.createDiffuse(0.34f, 0.21f, 0.13f, 1f),
                        ColorAttribute.createSpecular(0.16f, 0.12f, 0.09f, 1f),
                        FloatAttribute.createShininess(18f)), attrs);
        models.add(new ModelInstance(playingSurfaceModel, 0f, 0.02f, 0f));

        // Warm cloth/felt inset.
        Model felt = mb.createBox(
                17.55f, 0.18f, 9.05f,
                surface(0.055f, 0.042f, 0.036f), attrs);
        models.add(new ModelInstance(felt, 0f, 0.27f, 0f));

        railModel = mb.createBox(
                18.05f, 0.10f, 9.55f,
                woodTextured(0.42f, 0.34f, 0.27f, 40f), attrs);
        models.add(new ModelInstance(railModel, 0f, 0.35f, 0f));

        Model innerMat = mb.createBox(
                17.55f, 0.08f, 9.05f,
                surface(0.050f, 0.042f, 0.036f), attrs);
        models.add(new ModelInstance(innerMat, 0f, 0.405f, 0f));

        // Thin inner rails create a layered, furniture-grade edge around the felt.
        Material innerRailMat = woodTextured(0.46f, 0.37f, 0.29f, 50f);
        Model innerRailX = mb.createBox(16.80f, 0.075f, 0.12f, innerRailMat, attrs);
        Model innerRailZ = mb.createBox(0.12f, 0.075f, 8.95f, innerRailMat, attrs);
        models.add(new ModelInstance(innerRailX, 0f, 0.49f, -4.48f));
        models.add(new ModelInstance(innerRailX, 0f, 0.49f,  4.48f));
        models.add(new ModelInstance(innerRailZ, -8.34f, 0.49f, 0f));
        models.add(new ModelInstance(innerRailZ,  8.34f, 0.49f, 0f));

        // Subtle central divider shoulders, keeping the bar visually integrated.
        Model barShoulder = mb.createBox(
                0.92f, 0.07f, 9.02f,
                wood(0.22f, 0.060f, 0.016f, 30f), attrs);
        models.add(new ModelInstance(barShoulder, 0f, 0.54f, 0f));

        // Central bar with a subtle raised center strip.
        barModel = mb.createBox(
                0.72f, 0.22f, 8.95f,
                wood(0.12f, 0.034f, 0.012f, 28f), attrs);
        models.add(new ModelInstance(barModel, 0f, 0.48f, 0f));

        Model barHighlight = mb.createBox(
                0.16f, 0.045f, 8.55f,
                wood(0.58f, 0.22f, 0.050f, 40f), attrs);
        models.add(new ModelInstance(barHighlight, 0f, 0.61f, 0f));

        Material barCapMaterial = wood(0.34f, 0.11f, 0.022f, 46f);
        Model barCap = mb.createBox(0.28f, 0.035f, 8.35f, barCapMaterial, attrs);
        models.add(new ModelInstance(barCap, 0f, 0.665f, 0f));

        // Classic flat points: no wood texture is applied to these meshes.
        darkPointModel = createPointModel(
                new Material(
                        ColorAttribute.createDiffuse(0.30f, 0.16f, 0.085f, 1f),
                        ColorAttribute.createSpecular(0.10f, 0.10f, 0.10f, 1f),
                        FloatAttribute.createShininess(8f)),
                new Material(
                        ColorAttribute.createDiffuse(0.255f, 0.125f, 0.055f, 1f),
                        ColorAttribute.createSpecular(0.08f, 0.08f, 0.08f, 1f),
                        FloatAttribute.createShininess(6f)), attrs);
        lightPointModel = createPointModel(
                new Material(
                        ColorAttribute.createDiffuse(0.78f, 0.66f, 0.46f, 1f),
                        ColorAttribute.createSpecular(0.10f, 0.10f, 0.10f, 1f),
                        FloatAttribute.createShininess(8f)),
                new Material(
                        ColorAttribute.createDiffuse(0.64f, 0.50f, 0.31f, 1f),
                        ColorAttribute.createSpecular(0.08f, 0.08f, 0.08f, 1f),
                        FloatAttribute.createShininess(6f)), attrs);

        for (int i = 0; i < 12; i++) {
            ModelInstance bottomPoint = new ModelInstance(
                    (i % 2 == 0) ? darkPointModel : lightPointModel,
                    XS[i], 0.55f, -2.55f);
            bottomPoint.transform.rotate(Vector3.Y, 180f);
            models.add(bottomPoint);

            ModelInstance topPoint = new ModelInstance(
                    (i % 2 == 0) ? lightPointModel : darkPointModel,
                    XS[i], 0.55f, 2.55f);
            models.add(topPoint);
        }

        // Shared checker meshes. High radial resolution keeps the circular
        // silhouette clean on modern phone displays while remaining lightweight.
        darkChecker = createBeveledCheckerModel(
                new Material(
                        ColorAttribute.createDiffuse(0.045f, 0.028f, 0.020f, 1f),
                        ColorAttribute.createSpecular(0.30f, 0.23f, 0.17f, 1f),
                        FloatAttribute.createShininess(74f)),
                new Material(
                        ColorAttribute.createDiffuse(0.105f, 0.060f, 0.035f, 1f),
                        ColorAttribute.createSpecular(0.34f, 0.26f, 0.19f, 1f),
                        FloatAttribute.createShininess(88f)),
                new Material(
                        ColorAttribute.createDiffuse(0.18f, 0.095f, 0.050f, 1f),
                        ColorAttribute.createSpecular(0.44f, 0.32f, 0.21f, 1f),
                        FloatAttribute.createShininess(96f)),
                attrs);

        lightChecker = createBeveledCheckerModel(
                new Material(
                        ColorAttribute.createDiffuse(0.88f, 0.74f, 0.52f, 1f),
                        ColorAttribute.createSpecular(0.72f, 0.60f, 0.42f, 1f),
                        FloatAttribute.createShininess(64f)),
                new Material(
                        ColorAttribute.createDiffuse(0.70f, 0.50f, 0.29f, 1f),
                        ColorAttribute.createSpecular(0.58f, 0.46f, 0.30f, 1f),
                        FloatAttribute.createShininess(78f)),
                new Material(
                        ColorAttribute.createDiffuse(0.94f, 0.84f, 0.64f, 1f),
                        ColorAttribute.createSpecular(0.82f, 0.70f, 0.48f, 1f),
                        FloatAttribute.createShininess(92f)),
                attrs);

        diceModel = createBeveledDieModel(attrs);
        dieDotModel = mb.createCylinder(
                0.145f, 0.022f, 0.145f, 32,
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

        // Premium dice tray: a shallow inset surround makes the dice feel
        // physically seated on the board instead of floating above it.
        diceTrayModel = mb.createBox(
                4.25f, 0.07f, 2.25f,
                wood(0.12f, 0.045f, 0.018f, 26f), attrs);
        models.add(new ModelInstance(diceTrayModel, 0f, 0.66f, 0f));

        Model diceTrayInset = mb.createBox(
                3.95f, 0.055f, 1.95f,
                surface(0.19f, 0.060f, 0.018f), attrs);
        models.add(new ModelInstance(diceTrayInset, 0f, 0.715f, 0f));

        // Raised brass trim around the dice well gives the center a crafted,
        // furniture-like finish while keeping the dice area visually clean.
        Material trayTrim = wood(0.66f, 0.34f, 0.075f, 48f);
        Model trayTrimX = mb.createBox(4.05f, 0.045f, 0.075f, trayTrim, attrs);
        Model trayTrimZ = mb.createBox(0.075f, 0.045f, 1.95f, trayTrim, attrs);
        models.add(new ModelInstance(trayTrimX, 0f, 0.79f, -1.00f));
        models.add(new ModelInstance(trayTrimX, 0f, 0.79f,  1.00f));
        models.add(new ModelInstance(trayTrimZ, -2.00f, 0.79f, 0f));
        models.add(new ModelInstance(trayTrimZ,  2.00f, 0.79f, 0f));

        // Deep side storage wells inspired by a real wooden backgammon case.
        // They remain part of the board shell, so the playfield keeps its clean silhouette.
        sideTrayModel = mb.createBox(
                1.45f, 0.16f, 9.00f,
                woodTextured(0.38f, 0.31f, 0.25f, 28f), attrs);
        sideTrayInsetModel = mb.createBox(
                1.12f, 0.07f, 8.55f,
                surface(0.055f, 0.018f, 0.010f), attrs);
        models.add(new ModelInstance(sideTrayModel, -9.72f, 0.20f, 0f));
        models.add(new ModelInstance(sideTrayModel,  9.72f, 0.20f, 0f));
        models.add(new ModelInstance(sideTrayInsetModel, -9.72f, 0.31f, 0f));
        models.add(new ModelInstance(sideTrayInsetModel,  9.72f, 0.31f, 0f));

        // Thin walnut lips make the wells read as routed recesses.
        Material trayLip = woodTextured(0.48f, 0.39f, 0.31f, 42f);
        Model trayLipX = mb.createBox(0.08f, 0.075f, 8.72f, trayLip, attrs);
        models.add(new ModelInstance(trayLipX, -9.06f, 0.39f, 0f));
        models.add(new ModelInstance(trayLipX, -10.38f, 0.39f, 0f));
        models.add(new ModelInstance(trayLipX,  9.06f, 0.39f, 0f));
        models.add(new ModelInstance(trayLipX, 10.38f, 0.39f, 0f));

        // Decorative medallions on each half of the board.
        medallionModel = mb.createCylinder(
                0.43f, 0.035f, 0.43f, 48,
                woodTextured(0.56f, 0.45f, 0.34f, 50f), attrs);
        medallionRingModel = mb.createCylinder(
                0.31f, 0.045f, 0.31f, 48,
                wood(0.72f, 0.46f, 0.15f, 58f), attrs);
        models.add(new ModelInstance(medallionModel, -4.10f, 0.69f, 0f));
        models.add(new ModelInstance(medallionModel,  4.10f, 0.69f, 0f));
        models.add(new ModelInstance(medallionRingModel, -4.10f, 0.725f, 0f));
        models.add(new ModelInstance(medallionRingModel,  4.10f, 0.725f, 0f));

        // Center hinge plates and brass fasteners make the bar feel like a real case seam.
        hingePlateModel = mb.createBox(
                0.48f, 0.055f, 1.35f,
                wood(0.58f, 0.34f, 0.10f, 62f), attrs);
        models.add(new ModelInstance(hingePlateModel, 0f, 0.70f, -1.75f));
        models.add(new ModelInstance(hingePlateModel, 0f, 0.70f,  1.75f));

        Model hingeScrew = mb.createCylinder(
                0.075f, 0.035f, 0.075f, 20,
                wood(0.76f, 0.52f, 0.20f, 70f), attrs);
        models.add(new ModelInstance(hingeScrew, -0.17f, 0.76f, -1.95f));
        models.add(new ModelInstance(hingeScrew,  0.17f, 0.76f, -1.95f));
        models.add(new ModelInstance(hingeScrew, -0.17f, 0.76f,  1.95f));
        models.add(new ModelInstance(hingeScrew,  0.17f, 0.76f,  1.95f));

        // Four small brass-like fasteners on the board corners.
        screwModel = mb.createCylinder(
                0.105f, 0.045f, 0.105f, 24,
                wood(0.78f, 0.50f, 0.17f, 58f), attrs);
        models.add(new ModelInstance(screwModel, -8.55f, 0.40f, -4.78f));
        models.add(new ModelInstance(screwModel,  8.55f, 0.40f, -4.78f));
        models.add(new ModelInstance(screwModel, -8.55f, 0.40f,  4.78f));
        models.add(new ModelInstance(screwModel,  8.55f, 0.40f,  4.78f));

        rebuildGameObjects();
    }

    private Model createBeveledDieModel(long attrs) {
        // A clean closed cube is intentionally used for the die body. It is
        // more robust on mobile GPUs than a partially chamfered custom mesh,
        // while the metallic pips and tray provide the premium detail.
        Material m = new Material(
                ColorAttribute.createDiffuse(0.965f, 0.945f, 0.885f, 1f),
                ColorAttribute.createSpecular(0.82f, 0.76f, 0.62f, 1f),
                FloatAttribute.createShininess(82f));
        return mb.createBox(1.16f, 1.16f, 1.16f, m, attrs);
    }

    private void triQuad(MeshPartBuilder p, Vector3 a, Vector3 b, Vector3 c, Vector3 d) {
        p.triangle(a, b, c);
        p.triangle(a, c, d);
    }

    private Model createBeveledCheckerModel(Material material, Material detailMaterial, Material rimMaterial, long attrs) {
        mb.begin();
        MeshPartBuilder p = mb.part("checker", GL20.GL_TRIANGLES, attrs, material);

        final int segments = 64;
        final float radius = 0.50f;
        final float bevelRadius = 0.082f;
        final float half = 0.205f;
        final float bevelY = 0.072f;

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

        // Integrated top medallion: one shared mesh per checker color, so
        // the detailing follows every animated checker without extra objects.
        MeshPartBuilder detail = mb.part("checker_detail", GL20.GL_TRIANGLES, attrs, detailMaterial);
        final float detailY = half + bevelY + 0.006f;
        addDisc(detail, 0.235f, detailY, 48);
        addRing(detail, 0.345f, 0.315f, detailY + 0.002f, 64);

        // Thin circumferential bands catch the key light and make the bevel
        // read as machined material rather than a flat cylinder.
        MeshPartBuilder rim = mb.part("checker_rim", GL20.GL_TRIANGLES, attrs, rimMaterial);
        addCylinderBand(rim, 0.503f, 0.150f, 0.174f, 64);
        addCylinderBand(rim, 0.503f, -0.174f, -0.150f, 64);

        return mb.end();
    }

    private void addCylinderBand(MeshPartBuilder p, float radius, float y0, float y1, int segments) {
        for (int i = 0; i < segments; i++) {
            float a0 = MathUtils.PI2 * i / segments;
            float a1 = MathUtils.PI2 * (i + 1) / segments;
            Vector3 a = new Vector3(MathUtils.cos(a0) * radius, y0, MathUtils.sin(a0) * radius);
            Vector3 b = new Vector3(MathUtils.cos(a1) * radius, y0, MathUtils.sin(a1) * radius);
            Vector3 c = new Vector3(MathUtils.cos(a1) * radius, y1, MathUtils.sin(a1) * radius);
            Vector3 d = new Vector3(MathUtils.cos(a0) * radius, y1, MathUtils.sin(a0) * radius);
            triQuad(p, a, b, c, d);
        }
    }

    private void addDisc(MeshPartBuilder p, float radius, float y, int segments) {
        Vector3 center = new Vector3(0f, y, 0f);
        for (int i = 0; i < segments; i++) {
            float a0 = MathUtils.PI2 * i / segments;
            float a1 = MathUtils.PI2 * (i + 1) / segments;
            Vector3 v0 = new Vector3(MathUtils.cos(a0) * radius, y, MathUtils.sin(a0) * radius);
            Vector3 v1 = new Vector3(MathUtils.cos(a1) * radius, y, MathUtils.sin(a1) * radius);
            p.triangle(center, v1, v0);
        }
    }

    private void addRing(MeshPartBuilder p, float outerRadius, float innerRadius, float y, int segments) {
        for (int i = 0; i < segments; i++) {
            float a0 = MathUtils.PI2 * i / segments;
            float a1 = MathUtils.PI2 * (i + 1) / segments;
            Vector3 o0 = new Vector3(MathUtils.cos(a0) * outerRadius, y, MathUtils.sin(a0) * outerRadius);
            Vector3 o1 = new Vector3(MathUtils.cos(a1) * outerRadius, y, MathUtils.sin(a1) * outerRadius);
            Vector3 in0 = new Vector3(MathUtils.cos(a0) * innerRadius, y, MathUtils.sin(a0) * innerRadius);
            Vector3 in1 = new Vector3(MathUtils.cos(a1) * innerRadius, y, MathUtils.sin(a1) * innerRadius);
            p.triangle(o0, o1, in0);
            p.triangle(o1, in1, in0);
        }
    }

    private Texture createBoardArtworkTexture() {
        final int W = 1024, H = 512;
        Pixmap p = new Pixmap(W, H, Pixmap.Format.RGBA8888);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float wave = (float)Math.sin(x * 0.035f + Math.sin(y * 0.018f) * 2.2f);
                float fine = (float)Math.sin(x * 0.17f + y * 0.012f);
                float n = wave * 0.018f + fine * 0.008f;
                p.setColor(new Color(0.29f + n, 0.145f + n * 0.65f, 0.075f + n * 0.40f, 1f));
                p.drawPixel(x, y);
            }
        }
        p.setColor(new Color(0.32f, 0.18f, 0.10f, 1f));
        p.fillRectangle(52, 24, 430, 464);
        p.fillRectangle(542, 24, 430, 464);

        float[] light = {0.79f, 0.58f, 0.32f};
        float[] dark = {0.34f, 0.075f, 0.045f};
        int left = 62, top = 36, bottom = 476, center = 256;
        for (int side = 0; side < 2; side++) {
            int sx = side == 0 ? left : 542;
            for (int i = 0; i < 6; i++) {
                int x0 = sx + i * 70, x1 = x0 + 70;
                drawTri(p, x0, top, x1, top, (i % 2 == 0) ? light : dark, center - 8);
                drawTri(p, x0, bottom, x1, bottom, (i % 2 == 0) ? dark : light, center + 8);
            }
        }
        drawMedallion(p, 256, 256);
        drawMedallion(p, 768, 256);
        Texture result = new Texture(p, true);
        p.dispose();
        return result;
    }

    private void drawTri(Pixmap p, int x0, int y0, int x1, int y1, float[] c, int tipY) {
        p.setColor(c[0], c[1], c[2], 1f);
        int xm = (x0 + x1) / 2;
        int edge = Math.min(y0, y1);
        p.fillTriangle(x0, edge, x1, edge, xm, tipY);
    }

    private void drawMedallion(Pixmap p, int cx, int cy) {
        p.setColor(0.78f, 0.57f, 0.31f, 1f);
        p.fillCircle(cx, cy, 58);
        p.setColor(0.28f, 0.14f, 0.07f, 1f);
        p.fillCircle(cx, cy, 50);
        p.setColor(0.82f, 0.63f, 0.36f, 1f);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            int x = (int)(cx + 43 * Math.cos(a));
            int y = (int)(cy + 43 * Math.sin(a));
            p.fillTriangle(cx, cy, x - 7, y - 7, x + 7, y + 7);
        }
        p.fillCircle(cx, cy, 10);
    }

    private Model createPointModel(Material material, Material detailMaterial, long attrs) {
        mb.begin();
        MeshPartBuilder p = mb.part("point", GL20.GL_TRIANGLES, attrs, material);

        final float w = 0.40f;
        final float y0 = 0f;
        final float y1 = 0.095f;
        final float zBase = 1.93f;
        final float zTip = -1.67f;

        Vector3 a0 = new Vector3(-w, y0, zBase);
        Vector3 b0 = new Vector3( w, y0, zBase);
        Vector3 c0 = new Vector3(0f, y0, zTip);
        Vector3 a1 = new Vector3(-w, y1, zBase);
        Vector3 b1 = new Vector3( w, y1, zBase);
        Vector3 c1 = new Vector3(0f, y1, zTip);

        // Explicit UVs on the visible face. V runs from the wide wooden
        // base to the point, so the grain follows the long axis naturally.
        VertexInfo va = new VertexInfo().set(
                a1, Vector3.Y, null, new Vector2(0.0f, 0.0f));
        VertexInfo vb = new VertexInfo().set(
                b1, Vector3.Y, null, new Vector2(1.0f, 0.0f));
        VertexInfo vc = new VertexInfo().set(
                c1, Vector3.Y, null, new Vector2(0.5f, 1.0f));
        p.triangle(va, vb, vc);

        // Keep the side walls closed; they do not need visible grain mapping.
        p.triangle(c0, b0, a0);
        p.triangle(a0, b0, b1);
        p.triangle(a0, b1, a1);
        p.triangle(b0, c0, c1);
        p.triangle(b0, c1, b1);
        p.triangle(c0, a0, a1);
        p.triangle(c0, a1, c1);

        // Keep the playing points as one clean, solid surface.
        // The previous raised inset created distracting banding/moire at
        // mobile resolutions and made the points look striped instead of
        // like the clean wood/felt in a premium physical board.
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
        float d = 0.305f;
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
        camera.fieldOfView = 36f;
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
            float progress = MathUtils.clamp(diceRollTime / 0.95f, 0f, 1f);
            float spin = (1220f + diceThrowStrength * 2.8f) * delta * (0.45f + progress * 0.75f);
            float settle = MathUtils.clamp(1f - diceRollTime / 0.95f, 0f, 1f);
            float liftScale = MathUtils.sin(MathUtils.PI * settle);
            float lateral = MathUtils.sin(diceRollElapsed * 11f) * (0.035f + diceThrowStrength * 0.00012f);
            if (dieInstanceA != null) {
                dieInstanceA.transform.rotate(Vector3.X, spin).rotate(Vector3.Y, spin * 0.65f);
                dieInstanceA.transform.rotate(Vector3.Z, MathUtils.sin(diceRollElapsed * 17f) * 0.9f);
                float liftA = liftScale * (0.34f + diceThrowStrength * 0.00032f);
                float offsetA = lateral;
                dieInstanceA.transform.translate(offsetA, liftA - lastDiceLiftA, -offsetA * 0.45f);
                lastDiceLiftA = liftA;
            }
            if (dieInstanceB != null) {
                dieInstanceB.transform.rotate(Vector3.X, -spin * 0.85f).rotate(Vector3.Z, spin);
                dieInstanceB.transform.rotate(Vector3.Y, MathUtils.cos(diceRollElapsed * 15f) * 0.85f);
                float liftB = liftScale * (0.29f + diceThrowStrength * 0.00028f);
                float offsetB = -lateral * 0.85f;
                dieInstanceB.transform.translate(offsetB, liftB - lastDiceLiftB, offsetB * 0.40f);
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
        Gdx.gl.glClearColor(0.020f, 0.018f, 0.016f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);

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
        float top = Math.min(94f, h * 0.14f);

        uiShape.begin(ShapeRenderer.ShapeType.Filled);
        uiShape.setColor(0.045f, 0.035f, 0.030f, 0.97f);
        uiShape.rect(0f, h - top, w, top);
        uiShape.setColor(0.58f, 0.40f, 0.20f, 0.90f);
        uiShape.rect(0f, h - top, w, 3f);
        float centerW = Math.min(760f, w * 0.56f);
        float left = (w - centerW) * 0.5f;
        uiShape.setColor(0.22f, 0.16f, 0.10f, 0.96f);
        uiShape.rect(left, h - top + 12f, centerW, top - 24f);
        uiShape.end();

        uiBatch.begin();
        uiFont.setColor(0.91f, 0.76f, 0.48f, 1f);
        uiFont.getData().setScale(1.30f);
        uiFont.draw(uiBatch, "BACKGAMMON", 24f, h - 30f);
        uiFont.getData().setScale(1.02f);
        uiFont.setColor(0.96f, 0.92f, 0.82f, 1f);
        uiFont.draw(uiBatch, "YOU", left + 28f, h - 34f);
        uiFont.draw(uiBatch, "CPU", left + centerW - 72f, h - 34f);
        uiFont.getData().setScale(1.22f);
        uiFont.setColor(0.98f, 0.90f, 0.68f, 1f);
        String diceText = diceRolled ? (diceRollTime > 0f ? "ROLLING" : dice[0] + "   " + dice[1]) : "—";
        uiLayout.setText(uiFont, diceText);
        uiFont.draw(uiBatch, uiLayout, left + centerW * 0.5f - uiLayout.width * 0.5f, h - 32f);
        uiFont.getData().setScale(0.92f);
        uiFont.setColor(0.76f, 0.70f, 0.60f, 1f);
        String score = "BAR  " + lightBar + " / " + darkBar + "     OFF  " + lightOff + " / " + darkOff;
        uiFont.draw(uiBatch, score, 24f, h - top - 16f);
        if (statusTimer > 0f || !diceRolled) {
            uiFont.setColor(0.78f, 0.80f, 0.84f, 1f);
            String hint = !diceRolled ? "Tap or throw the dice" : status;
            uiFont.draw(uiBatch, hint, 24f, 28f);
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
        if (woodTexture != null) woodTexture.dispose();
        if (boardArtworkTexture != null) boardArtworkTexture.dispose();
        if (floorModel != null) floorModel.dispose();
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
        if (diceTrayModel != null) diceTrayModel.dispose();
        if (diceEdgeModel != null) diceEdgeModel.dispose();
        if (screwModel != null) screwModel.dispose();
        if (sideTrayModel != null) sideTrayModel.dispose();
        if (sideTrayInsetModel != null) sideTrayInsetModel.dispose();
        if (hingePlateModel != null) hingePlateModel.dispose();
        if (medallionModel != null) medallionModel.dispose();
        if (medallionRingModel != null) medallionRingModel.dispose();
    }
}
