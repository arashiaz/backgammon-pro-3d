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
import com.badlogic.gdx.graphics.glutils.FacedCubemapData;
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
    private static final float[] XS = {-5.775f, -4.725f, -3.675f, -2.625f, -1.575f, -0.525f,
                                       0.525f,  1.575f,  2.625f,  3.675f,  4.725f,  5.775f};
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
    private Model darkPointModel, lightPointModel, pointShadowModel;
    private Model darkChecker, lightChecker;
    private Model diceModel, dieDotModel, checkerShadowModel;
    private Model accentModel;
    private Model diceTrayModel, screwModel;
    private Model diceEdgeModel;
    private Texture woodGrainTexture, woodNormalTexture;
    private Texture lightCheckerTexture, lightCheckerNormalTexture;
    private Texture darkCheckerTexture, darkCheckerNormalTexture;
    private Texture diceTexture, diceNormalTexture;
    private Cubemap studioCubemap;
    
    private float lastX, lastY;
    private boolean dragging;
    private float cameraAzimuth = 0f;
    private float cameraElevation = 55f;
    private float cameraDistance = 18.2f;

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
                ColorAttribute.AmbientLight, 0.30f, 0.29f, 0.27f, 1f));
        // Large warm key: stronger grazing highlights reveal the real walnut grain,
        // checker bevels and the rounded dice without washing the scene out.
        environment.add(new DirectionalLight().set(
                1.30f, 1.12f, 0.92f, -0.52f, -1.0f, -0.28f));
        // Soft neutral fill keeps the shadow side readable on mobile displays.
        environment.add(new DirectionalLight().set(
                0.22f, 0.24f, 0.28f, 0.48f, -0.58f, 0.64f));
        // Very low warm rim light separates the outer rails from the dark floor.
        environment.add(new DirectionalLight().set(
                0.10f, 0.075f, 0.045f, 0.05f, -0.35f, -0.94f));

        woodGrainTexture = new Texture(
                Gdx.files.internal("textures/board_wood_texture.jpg"), true);
        woodGrainTexture.setFilter(
                Texture.TextureFilter.MipMapLinearLinear,
                Texture.TextureFilter.Linear);
        woodGrainTexture.setWrap(
                Texture.TextureWrap.Repeat,
                Texture.TextureWrap.Repeat);
        // Premium palette: warm aged ivory, deep oxblood resin, natural bone.
        lightCheckerTexture = createPieceTexture(256, 0.90f, 0.84f, 0.72f, 1.00f, 0.97f, 0.88f, 101L);
        darkCheckerTexture = createPieceTexture(256, 0.16f, 0.026f, 0.032f, 0.48f, 0.085f, 0.075f, 202L);
        diceTexture = createPieceTexture(256, 0.91f, 0.82f, 0.67f, 0.99f, 0.91f, 0.76f, 303L);
        woodNormalTexture = createNormalTexture(256, 11f, 0.55f, 404L);
        lightCheckerNormalTexture = createNormalTexture(256, 5f, 0.34f, 505L);
        darkCheckerNormalTexture = createNormalTexture(256, 5f, 0.34f, 606L);
        diceNormalTexture = createNormalTexture(256, 4f, 0.22f, 707L);
        studioCubemap = createStudioCubemap();
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
        // Intersect at the top of the dice/tray region. Using the actual
        // projected die centers avoids the old board-scale mismatch that made
        // only a small corner respond to touch.
        Plane plane = new Plane(Vector3.Y, 0.46f);
        if (!Intersector.intersectRayPlane(ray, plane, tmp)) return false;
        final float r2 = 0.72f * 0.72f;
        float dxA = tmp.x + 0.48f;
        float dxB = tmp.x - 0.48f;
        return dxA * dxA + tmp.z * tmp.z <= r2
                || dxB * dxB + tmp.z * tmp.z <= r2;
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

        dieInstanceA = new ModelInstance(diceModel, -0.48f, 1.12f, 0f);
        dieInstanceB = new ModelInstance(diceModel,  0.48f, 1.12f, 0f);
        dieInstanceA.transform.rotate(Vector3.Y, -9f);
        dieInstanceB.transform.rotate(Vector3.Y, 12f);
        gameObjects.add(dieInstanceA); models.add(dieInstanceA);
        gameObjects.add(dieInstanceB); models.add(dieInstanceB);
        // During the roll the cubes are intentionally clean: pips are added
        // only after the final face is settled, so they never float while the
        // cube spins. The final face is rebuilt atomically when the animation ends.
        if (diceRollTime <= 0f) {
            if (dice[0] > 0) addTopPips(-0.48f, 1.504f, 0f, dice[0]);
            if (dice[1] > 0) addTopPips( 0.48f, 1.504f, 0f, dice[1]);
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
                float z = p < 12 ? -2.92f + i * 0.22f : 2.92f - i * 0.22f;
                ModelInstance shadow = new ModelInstance(checkerShadowModel, x, 0.505f + i * 0.22f, z);
                shadow.transform.scl(1.08f, 1f, 0.78f);
                gameObjects.add(shadow); models.add(shadow);
                ModelInstance piece = new ModelInstance(model, x, 0.58f + i * 0.22f, z);
                gameObjects.add(piece); models.add(piece);
            }
        }
        for (int i = 0; i < lightBar; i++) {
            ModelInstance shadow = new ModelInstance(checkerShadowModel, -0.95f, 0.705f + i * 0.43f, 0f);
            shadow.transform.scl(1.08f, 1f, 0.78f);
            gameObjects.add(shadow); models.add(shadow);
            ModelInstance piece = new ModelInstance(lightChecker, -0.95f, 0.78f + i * 0.43f, 0f);
            gameObjects.add(piece); models.add(piece);
        }
        for (int i = 0; i < darkBar; i++) {
            ModelInstance shadow = new ModelInstance(checkerShadowModel, 0.95f, 0.705f + i * 0.43f, 0f);
            shadow.transform.scl(1.08f, 1f, 0.78f);
            gameObjects.add(shadow); models.add(shadow);
            ModelInstance piece = new ModelInstance(darkChecker, 0.95f, 0.78f + i * 0.43f, 0f);
            gameObjects.add(piece); models.add(piece);
        }
    }

    private ModelInstance findTopChecker(int point) {
        int count = Math.abs(points[point]);
        if (count <= 0) return null;
        int col = point < 12 ? point : 23 - point;
        float x = XS[col];
        float z = point < 12 ? -2.92f + (count - 1) * 0.22f : 2.92f - (count - 1) * 0.22f;
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
        float z = point < 12 ? -2.92f + stackIndex * 0.22f : 2.92f - stackIndex * 0.22f;
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
                float z = to < 12 ? -2.82f : 2.82f;
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
            float pz = p < 12 ? -2.82f : 2.82f;
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

    private Texture createNormalTexture(int size, float frequency, float strength, long seed) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            float u = x / (float)(size - 1), v = y / (float)(size - 1);
            float hL = materialHeight(u - 1f / size, v, frequency, seed);
            float hR = materialHeight(u + 1f / size, v, frequency, seed);
            float hD = materialHeight(u, v - 1f / size, frequency, seed);
            float hU = materialHeight(u, v + 1f / size, frequency, seed);
            float nx = (hL - hR) * strength, ny = (hD - hU) * strength, nz = 1f;
            float len = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
            int r = (int)((nx / len * 0.5f + 0.5f) * 255f);
            int g = (int)((ny / len * 0.5f + 0.5f) * 255f);
            int b = (int)((nz / len * 0.5f + 0.5f) * 255f);
            pm.drawPixel(x, y, Color.rgba8888(r / 255f, g / 255f, b / 255f, 1f));
        }
        Texture t = new Texture(pm, true);
        t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        pm.dispose();
        return t;
    }

    private float materialHeight(float u, float v, float frequency, long seed) {
        float x = u * frequency * 18f + (seed % 97) * 0.17f;
        float y = v * frequency * 18f + (seed % 53) * 0.11f;
        return smoothNoise(x, y) * 0.72f + smoothNoise(x * 2.7f, y * 2.7f) * 0.28f;
    }

    private Texture createNaturalWoodTexture(int size) {
        // Soft, irregular walnut grain. The pattern is deliberately aperiodic:
        // no sine bands, no repeated horizontal stripes, and only subtle contrast.
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        for (int y = 0; y < size; y++) {
            float v = y / (float) (size - 1);
            for (int x = 0; x < size; x++) {
                float u = x / (float) (size - 1);

                float warp = (smoothNoise(u * 2.4f, v * 2.4f) - 0.5f) * 0.75f;
                float grain = smoothNoise(
                        u * 3.2f + warp,
                        v * 13.0f + warp * 0.55f);

                float broad = smoothNoise(
                        u * 1.8f + warp * 0.35f,
                        v * 4.0f + warp * 0.25f);

                float pore = smoothNoise(
                        u * 22.0f + warp,
                        v * 30.0f - warp);

                // Mostly broad natural variation, with restrained directional grain.
                float variation = (broad - 0.5f) * 0.12f
                        + (grain - 0.5f) * 0.075f
                        + (pore - 0.5f) * 0.018f;

                float r = MathUtils.clamp(0.34f + variation * 0.90f, 0f, 1f);
                float g = MathUtils.clamp(0.16f + variation * 0.55f, 0f, 1f);
                float b = MathUtils.clamp(0.075f + variation * 0.32f, 0f, 1f);

                pm.setColor(r, g, b, 1f);
                pm.drawPixel(x, y);
            }
        }

        Texture tex = new Texture(pm, true);
        tex.setFilter(Texture.TextureFilter.MipMapLinearLinear,
                Texture.TextureFilter.Linear);
        tex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        pm.dispose();
        return tex;
    }

    /** Consistent Android-friendly material pipeline built on LibGDX DefaultShader. */
    private Material material(Texture albedo, Texture normal, float r, float g, float b,
                              float sr, float sg, float sb, float shininess) {
        Material m = new Material(ColorAttribute.createDiffuse(r, g, b, 1f));
        if (albedo != null) m.set(TextureAttribute.createDiffuse(albedo));
        if (normal != null) m.set(TextureAttribute.createNormal(normal));
        m.set(ColorAttribute.createSpecular(sr, sg, sb, 1f));
        m.set(FloatAttribute.createShininess(shininess));
        return m;
    }

    private Material mahoganyMaterial(float r, float g, float b) {
        return material(woodGrainTexture, woodNormalTexture, r, g, b, 0.42f, 0.27f, 0.20f, 44f);
    }

    private Material playingWoodMaterial() {
        // Finished walnut playfield: restrained warm tint, moderate specular
        // response and a tighter highlight so the surface reads as varnished
        // wood instead of a matte brown panel.
        return material(woodGrainTexture, woodNormalTexture,
                0.78f, 0.53f, 0.315f,
                0.62f, 0.40f, 0.24f, 72f);
    }

    private Material glossyDarkResinMaterial() {
        Material m = material(darkCheckerTexture, darkCheckerNormalTexture,
                0.42f, 0.075f, 0.070f, 0.92f, 0.48f, 0.40f, 132f);
        applyStudioReflection(m, 0.24f, 0.10f, 0.08f);
        return m;
    }

    private Material glossyIvoryResinMaterial() {
        Material m = material(lightCheckerTexture, lightCheckerNormalTexture,
                0.96f, 0.89f, 0.77f, 0.92f, 0.78f, 0.58f, 118f);
        applyStudioReflection(m, 0.22f, 0.18f, 0.12f);
        return m;
    }

    private Material boneDiceMaterial() {
        Material m = material(diceTexture, diceNormalTexture,
                0.96f, 0.87f, 0.69f, 0.94f, 0.80f, 0.60f, 104f);
        applyStudioReflection(m, 0.20f, 0.16f, 0.10f);
        return m;
    }

    private void applyStudioReflection(Material material, float r, float g, float b) {
        if (studioCubemap == null) return;
        material.set(new CubemapAttribute(CubemapAttribute.EnvironmentMap, studioCubemap));
        material.set(ColorAttribute.createReflection(r, g, b, 1f));
    }

    /**
     * Tiny procedural studio environment used only for subtle glossy reflections.
     * It avoids shipping another texture while giving resin and bone a controlled
     * softbox highlight instead of a flat directional-light response.
     */
    private Cubemap createStudioCubemap() {
        final int size = 32;
        Pixmap[] faces = new Pixmap[6];
        float[] faceBoost = {1.00f, 0.82f, 1.18f, 0.62f, 0.92f, 0.74f};
        for (int f = 0; f < faces.length; f++) {
            Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
            float boost = faceBoost[f];
            for (int y = 0; y < size; y++) {
                float v = y / (float)(size - 1);
                float softbox = (float)Math.exp(-Math.pow((v - 0.32f) / 0.18f, 2.0));
                for (int x = 0; x < size; x++) {
                    float u = x / (float)(size - 1);
                    float edge = 1f - Math.abs(u - 0.5f) * 0.34f;
                    float value = MathUtils.clamp((0.055f + softbox * 0.18f) * boost * edge, 0f, 1f);
                    pm.setColor(value * 0.98f, value * 0.91f, value * 0.78f, 1f);
                    pm.drawPixel(x, y);
                }
            }
            faces[f] = pm;
        }
        Cubemap cubemap = new Cubemap(new FacedCubemapData(
                faces[0], faces[1], faces[2], faces[3], faces[4], faces[5], false));
        for (Pixmap face : faces) face.dispose();
        return cubemap;
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
        Material m = new Material(
                ColorAttribute.createDiffuse(r, g, b, 1f),
                TextureAttribute.createDiffuse(woodGrainTexture));
        m.set(ColorAttribute.createSpecular(
                Math.min(1f, r + 0.14f),
                Math.min(1f, g + 0.14f),
                Math.min(1f, b + 0.14f), 1f));
        m.set(FloatAttribute.createShininess(shine));
        return m;
    }


    private Texture createPieceTexture(int size, float br, float bg, float bb,
                                       float vr, float vg, float vb, long seed) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        float ox = (seed % 97) * 0.137f;
        float oy = (seed % 53) * 0.193f;
        for (int y = 0; y < size; y++) {
            float v = y / (float)(size - 1);
            for (int x = 0; x < size; x++) {
                float u = x / (float)(size - 1);
                float n1 = smoothNoise(u * 3.0f + ox, v * 3.0f + oy);
                float n2 = smoothNoise(u * 8.0f + ox * 0.7f, v * 5.0f + oy * 0.8f);
                float n3 = smoothNoise(u * 28.0f + ox, v * 28.0f + oy);
                float vein = MathUtils.clamp(
                        (n1 - 0.5f) * 0.55f + (n2 - 0.5f) * 0.22f + (n3 - 0.5f) * 0.06f,
                        -0.32f, 0.32f);
                float r = MathUtils.clamp(br + (vr - br) * (0.48f + vein), 0f, 1f);
                float g = MathUtils.clamp(bg + (vg - bg) * (0.48f + vein), 0f, 1f);
                float b = MathUtils.clamp(bb + (vb - bb) * (0.48f + vein), 0f, 1f);
                pm.setColor(r, g, b, 1f);
                pm.drawPixel(x, y);
            }
        }
        Texture tex = new Texture(pm, true);
        tex.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        tex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        pm.dispose();
        return tex;
    }

    private Material surface(float r, float g, float b) {
        Material m = new Material(ColorAttribute.createDiffuse(r, g, b, 1f));
        m.set(ColorAttribute.createSpecular(0.12f, 0.10f, 0.08f, 1f));
        m.set(FloatAttribute.createShininess(10f));
        return m;
    }

    private void buildBoard() {
        final long attrs = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates;

        // A dark furniture-like floor grounds the board in the scene instead
        // of leaving it floating against a flat black background.
        floorModel = mb.createBox(
                20.0f, 0.10f, 12.0f,
                surface(0.018f, 0.022f, 0.028f), attrs);
        models.add(new ModelInstance(floorModel, 0f, -0.84f, 0f));

        // Layered wooden frame: darker body + inset + thin highlight rail.
        baseModel = mb.createBox(
                14.8f, 0.64f, 8.6f,
                material(woodGrainTexture, woodNormalTexture,
                        0.54f, 0.30f, 0.19f,
                        0.52f, 0.33f, 0.20f, 58f), attrs);
        models.add(new ModelInstance(baseModel, 0f, -0.02f, 0f));

        // Single clean playfield: one solid top surface avoids layered
        // coplanar/intersecting panels that can create mobile depth artifacts.
        playingSurfaceModel = mb.createBox(
                12.70f, 0.18f, 7.60f,
                playingWoodMaterial(), attrs);
        models.add(new ModelInstance(playingSurfaceModel, 0f, 0.38f, 0f));

        // No overlay rail over the playfield. Keeping the playing surface as
        // one visible mesh prevents the repeated-line artifact seen on mobile.

        // Hand-finished perimeter rails: a raised inner lip plus a darker
        // shadow seam makes the playfield read as a routed wooden case rather
        // than a stack of flat boxes.
        Material frameRail = woodTextured(0.52f, 0.30f, 0.18f, 52f);
        Material frameLip = mahoganyMaterial(0.30f, 0.115f, 0.040f);
        Model railLong = mb.createBox(
                13.30f, 0.16f, 0.34f, frameRail, attrs);
        Model railShort = mb.createBox(
                0.34f, 0.16f, 7.30f, frameRail, attrs);
        models.add(new ModelInstance(railLong, 0f, 0.53f, -4.02f));
        models.add(new ModelInstance(railLong, 0f, 0.53f,  4.02f));
        models.add(new ModelInstance(railShort, -6.48f, 0.53f, 0f));
        models.add(new ModelInstance(railShort,  6.48f, 0.53f, 0f));

        Model lipLong = mb.createBox(
                12.92f, 0.045f, 0.055f, frameLip, attrs);
        Model lipShort = mb.createBox(
                0.055f, 0.045f, 7.18f, frameLip, attrs);
        models.add(new ModelInstance(lipLong, 0f, 0.635f, -3.82f));
        models.add(new ModelInstance(lipLong, 0f, 0.635f,  3.82f));
        models.add(new ModelInstance(lipShort, -6.30f, 0.635f, 0f));
        models.add(new ModelInstance(lipShort,  6.30f, 0.635f, 0f));

        // Subtle central divider shoulders, keeping the bar visually integrated.
        Model barShoulder = mb.createBox(
                0.86f, 0.07f, 7.48f,
                wood(0.22f, 0.060f, 0.016f, 30f), attrs);
        models.add(new ModelInstance(barShoulder, 0f, 0.56f, 0f));

        // Central bar with a subtle raised center strip.
        barModel = mb.createBox(
                0.82f, 0.22f, 7.42f,
                wood(0.20f, 0.055f, 0.025f, 34f), attrs);
        models.add(new ModelInstance(barModel, 0f, 0.54f, 0f));

        Model barHighlight = mb.createBox(
                0.12f, 0.045f, 7.10f,
                wood(0.58f, 0.22f, 0.050f, 40f), attrs);
        models.add(new ModelInstance(barHighlight, 0f, 0.67f, 0f));

        Material barCapMaterial = wood(0.34f, 0.11f, 0.022f, 46f);
        Model barCap = mb.createBox(0.24f, 0.035f, 6.95f, barCapMaterial, attrs);
        models.add(new ModelInstance(barCap, 0f, 0.725f, 0f));

        // Classic flat points: no wood texture is applied to these meshes.
        // Lacquered resin-style point inlays: a deep burgundy and warm ivory
        // with controlled specular response so the raised faces catch the light
        // like finished furniture rather than flat 2D paint.
        darkPointModel = createPointModel(
                new Material(
                        ColorAttribute.createDiffuse(0.27f, 0.055f, 0.075f, 1f),
                        ColorAttribute.createSpecular(0.48f, 0.25f, 0.22f, 1f),
                        FloatAttribute.createShininess(58f)),
                new Material(
                        ColorAttribute.createDiffuse(0.12f, 0.022f, 0.030f, 1f),
                        ColorAttribute.createSpecular(0.62f, 0.34f, 0.28f, 1f),
                        FloatAttribute.createShininess(78f)), attrs);
        lightPointModel = createPointModel(
                new Material(
                        ColorAttribute.createDiffuse(0.86f, 0.72f, 0.49f, 1f),
                        ColorAttribute.createSpecular(0.52f, 0.42f, 0.28f, 1f),
                        FloatAttribute.createShininess(62f)),
                new Material(
                        ColorAttribute.createDiffuse(0.67f, 0.48f, 0.27f, 1f),
                        ColorAttribute.createSpecular(0.70f, 0.52f, 0.30f, 1f),
                        FloatAttribute.createShininess(82f)), attrs);

        // Thin dark routed bed beneath every point. It is deliberately larger than
        // the inlay and only a fraction of the height, creating a natural
        // recessed/AO edge instead of a flat painted triangle.
        pointShadowModel = createPointModel(
                new Material(
                        ColorAttribute.createDiffuse(0.055f, 0.018f, 0.010f, 1f),
                        ColorAttribute.createSpecular(0.12f, 0.055f, 0.025f, 1f),
                        FloatAttribute.createShininess(24f)),
                new Material(
                        ColorAttribute.createDiffuse(0.030f, 0.010f, 0.006f, 1f),
                        ColorAttribute.createSpecular(0.08f, 0.035f, 0.018f, 1f),
                        FloatAttribute.createShininess(18f)), attrs);

        for (int i = 0; i < 12; i++) {
            ModelInstance bottomShadow = new ModelInstance(
                    pointShadowModel, XS[i], 0.465f, -2.18f);
            bottomShadow.transform.rotate(Vector3.Y, 180f);
            bottomShadow.transform.scale(1.06f, 0.34f, 1.06f);
            models.add(bottomShadow);

            ModelInstance topShadow = new ModelInstance(
                    pointShadowModel, XS[i], 0.465f, 2.18f);
            topShadow.transform.scale(1.06f, 0.34f, 1.06f);
            models.add(topShadow);

            ModelInstance bottomPoint = new ModelInstance(
                    (i % 2 == 0) ? darkPointModel : lightPointModel,
                    XS[i], 0.50f, -2.18f);
            bottomPoint.transform.rotate(Vector3.Y, 180f);
            models.add(bottomPoint);

            ModelInstance topPoint = new ModelInstance(
                    (i % 2 == 0) ? lightPointModel : darkPointModel,
                    XS[i], 0.50f, 2.18f);
            models.add(topPoint);
        }

        // Shared checker meshes. High radial resolution keeps the circular
        // silhouette clean on modern phone displays while remaining lightweight.
        darkChecker = createBeveledCheckerModel(darkCheckerTexture,
                new Material(
                        ColorAttribute.createDiffuse(0.075f, 0.012f, 0.018f, 1f),
                        ColorAttribute.createSpecular(0.88f, 0.34f, 0.30f, 1f),
                        FloatAttribute.createShininess(138f)),
                new Material(
                        ColorAttribute.createDiffuse(0.28f, 0.022f, 0.030f, 1f),
                        ColorAttribute.createSpecular(0.96f, 0.48f, 0.42f, 1f),
                        FloatAttribute.createShininess(152f)),
                new Material(
                        ColorAttribute.createDiffuse(0.52f, 0.045f, 0.055f, 1f),
                        ColorAttribute.createSpecular(1.0f, 0.58f, 0.50f, 1f),
                        FloatAttribute.createShininess(166f)),
                attrs);

        lightChecker = createBeveledCheckerModel(lightCheckerTexture,
                new Material(
                        ColorAttribute.createDiffuse(0.84f, 0.76f, 0.60f, 1f),
                        ColorAttribute.createSpecular(0.86f, 0.74f, 0.52f, 1f),
                        FloatAttribute.createShininess(118f)),
                new Material(
                        ColorAttribute.createDiffuse(0.98f, 0.91f, 0.73f, 1f),
                        ColorAttribute.createSpecular(0.98f, 0.86f, 0.62f, 1f),
                        FloatAttribute.createShininess(134f)),
                new Material(
                        ColorAttribute.createDiffuse(0.68f, 0.47f, 0.25f, 1f),
                        ColorAttribute.createSpecular(0.72f, 0.54f, 0.32f, 1f),
                        FloatAttribute.createShininess(104f)),
                attrs);

        diceModel = createBeveledDieModel(diceTexture, attrs);
        dieDotModel = mb.createCylinder(
                0.071f, 0.012f, 0.071f, 32,
                new Material(
                        ColorAttribute.createDiffuse(0.028f, 0.020f, 0.015f, 1f),
                        ColorAttribute.createSpecular(0.10f, 0.075f, 0.050f, 1f),
                        FloatAttribute.createShininess(18f)),
                attrs);

        // Soft contact shadow under every checker. It is intentionally subtle:
        // enough to visually seat the pieces on the wood without looking painted on.
        checkerShadowModel = mb.createCylinder(
                0.43f, 0.006f, 0.43f, 40,
                new Material(
                        ColorAttribute.createDiffuse(0.015f, 0.010f, 0.008f, 0.24f),
                        new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.24f)),
                attrs);

        // Small gold center emblem/trim.
        accentModel = mb.createCylinder(
                0.16f, 0.035f, 0.16f, 32,
                wood(0.78f, 0.50f, 0.17f, 52f), attrs);
        models.add(new ModelInstance(accentModel, 0f, 0.74f, 0f));

        // Premium dice tray: a shallow inset surround makes the dice feel
        // physically seated on the board instead of floating above it.
        diceTrayModel = mb.createBox(
                2.55f, 0.07f, 1.55f,
                mahoganyMaterial(0.20f, 0.065f, 0.028f), attrs);
        models.add(new ModelInstance(diceTrayModel, 0f, 0.66f, 0f));

        Model diceTrayInset = mb.createBox(
                2.30f, 0.055f, 1.25f,
                surface(0.18f, 0.055f, 0.025f), attrs);
        models.add(new ModelInstance(diceTrayInset, 0f, 0.715f, 0f));

        // Raised brass trim around the dice well gives the center a crafted,
        // furniture-like finish while keeping the dice area visually clean.
        Material trayTrim = mahoganyMaterial(0.68f, 0.30f, 0.075f);
        Model trayTrimX = mb.createBox(2.38f, 0.045f, 0.06f, trayTrim, attrs);
        Model trayTrimZ = mb.createBox(0.06f, 0.045f, 1.25f, trayTrim, attrs);
        models.add(new ModelInstance(trayTrimX, 0f, 0.79f, -0.64f));
        models.add(new ModelInstance(trayTrimX, 0f, 0.79f,  0.64f));
        models.add(new ModelInstance(trayTrimZ, -1.19f, 0.79f, 0f));
        models.add(new ModelInstance(trayTrimZ,  1.19f, 0.79f, 0f));

        // Deep side storage wells inspired by a real wooden backgammon case.
        // They remain part of the board shell, so the playfield keeps its clean silhouette.
        sideTrayModel = mb.createBox(
                0.72f, 0.16f, 7.70f,
                woodTextured(0.38f, 0.31f, 0.25f, 28f), attrs);
        sideTrayInsetModel = mb.createBox(
                0.50f, 0.07f, 7.25f,
                surface(0.055f, 0.018f, 0.010f), attrs);
        models.add(new ModelInstance(sideTrayModel, -7.02f, 0.20f, 0f));
        models.add(new ModelInstance(sideTrayModel,  7.02f, 0.20f, 0f));
        models.add(new ModelInstance(sideTrayInsetModel, -7.02f, 0.31f, 0f));
        models.add(new ModelInstance(sideTrayInsetModel,  7.02f, 0.31f, 0f));

        // Thin walnut lips make the wells read as routed recesses.
        Material trayLip = woodTextured(0.48f, 0.39f, 0.31f, 42f);
        Model trayLipX = mb.createBox(0.06f, 0.075f, 7.42f, trayLip, attrs);
        models.add(new ModelInstance(trayLipX, -6.63f, 0.39f, 0f));
        models.add(new ModelInstance(trayLipX, -7.34f, 0.39f, 0f));
        models.add(new ModelInstance(trayLipX,  6.63f, 0.39f, 0f));
        models.add(new ModelInstance(trayLipX,  7.34f, 0.39f, 0f));

        // Decorative medallions on each half of the board.
        medallionModel = mb.createCylinder(
                0.43f, 0.035f, 0.43f, 48,
                woodTextured(0.56f, 0.45f, 0.34f, 50f), attrs);
        medallionRingModel = mb.createCylinder(
                0.31f, 0.045f, 0.31f, 48,
                wood(0.72f, 0.46f, 0.15f, 58f), attrs);
        // Decorative hardware omitted from the gameplay surface.

        // Center hinge plates and brass fasteners make the bar feel like a real case seam.
        hingePlateModel = mb.createBox(
                0.48f, 0.055f, 1.35f,
                wood(0.58f, 0.34f, 0.10f, 62f), attrs);
        // Decorative hardware omitted from the gameplay surface.

        Model hingeScrew = mb.createCylinder(
                0.075f, 0.035f, 0.075f, 20,
                wood(0.76f, 0.52f, 0.20f, 70f), attrs);
        // Decorative hardware omitted from the gameplay surface.

        // Four small brass-like fasteners on the board corners.
        screwModel = mb.createCylinder(
                0.105f, 0.045f, 0.105f, 24,
                wood(0.78f, 0.50f, 0.17f, 58f), attrs);
        // Decorative hardware omitted from the gameplay surface.

        rebuildGameObjects();
    }

    private Model createBeveledDieModel(Texture texture, long attrs) {
        // Low-cost premium die: an octagonal rounded-rectangle profile with
        // real bevel bands on every edge. This reads much closer to molded
        // ivory/resin than a sharp LibGDX box while remaining mobile-friendly.
        Material m = boneDiceMaterial();

        final float half = 0.38f;
        final float inset = 0.070f;
        final float bevelY = 0.078f;

        // Eight perimeter points: chamfered corners prevent razor-sharp cube
        // corners without the vertex cost of a full rounded-cube subdivision.
        float[][] outline = {
                {-half + inset, -half}, { half - inset, -half},
                { half, -half + inset}, { half,  half - inset},
                { half - inset,  half}, {-half + inset,  half},
                {-half,  half - inset}, {-half, -half + inset}
        };

        mb.begin();

        MeshPartBuilder body = mb.part("die_body", GL20.GL_TRIANGLES, attrs, m);

        Vector3[] top = new Vector3[8];
        Vector3[] topBevel = new Vector3[8];
        Vector3[] bottomBevel = new Vector3[8];
        Vector3[] bottom = new Vector3[8];

        for (int i = 0; i < 8; i++) {
            float x = outline[i][0];
            float z = outline[i][1];
            float sx = MathUtils.clamp(x, -half + inset, half - inset);
            float sz = MathUtils.clamp(z, -half + inset, half - inset);

            top[i] = new Vector3(x * 0.84f, half, z * 0.84f);
            topBevel[i] = new Vector3(x, half - bevelY, z);
            bottomBevel[i] = new Vector3(x, -half + bevelY, z);
            bottom[i] = new Vector3(x * 0.84f, -half, z * 0.84f);
        }

        // Top and bottom caps.
        for (int i = 1; i < 7; i++) {
            body.triangle(top[0], top[i], top[i + 1]);
            body.triangle(bottom[0], bottom[i + 1], bottom[i]);
        }

        // Four bevel bands plus four straight side bands.
        for (int i = 0; i < 8; i++) {
            int j = (i + 1) % 8;
            body.triangle(top[i], topBevel[i], topBevel[j]);
            body.triangle(top[i], topBevel[j], top[j]);

            body.triangle(topBevel[i], bottomBevel[i], bottomBevel[j]);
            body.triangle(topBevel[i], bottomBevel[j], topBevel[j]);

            body.triangle(bottomBevel[i], bottom[i], bottom[j]);
            body.triangle(bottomBevel[i], bottom[j], bottomBevel[j]);
        }

        return mb.end();
    }

    private void triQuad(MeshPartBuilder p, Vector3 a, Vector3 b, Vector3 c, Vector3 d) {
        p.triangle(a, b, c);
        p.triangle(a, c, d);
    }

    private Model createBeveledCheckerModel(Texture topTexture, Material material, Material detailMaterial, Material rimMaterial, long attrs) {
        mb.begin();
        MeshPartBuilder p = mb.part("checker", GL20.GL_TRIANGLES, attrs, material);

        final int segments = 32;
        final float radius = 0.39f;
        final float bevelRadius = 0.045f;
        final float half = 0.095f;
        final float bevelY = 0.040f;

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
            p.triangle(new Vector3(0f, -half - bevelY, 0f), bot0, bot1);
            p.triangle(topC0, botC0, botC1);
            p.triangle(topC0, botC1, topC1);
            p.triangle(top0, top1, bot1);
            p.triangle(top0, bot1, bot0);
        }

        MeshPartBuilder top = mb.part("checker_top", GL20.GL_TRIANGLES, attrs,
                new Material(
                        TextureAttribute.createDiffuse(topTexture),
                        TextureAttribute.createNormal(
                                topTexture == lightCheckerTexture
                                        ? lightCheckerNormalTexture : darkCheckerNormalTexture),
                        ColorAttribute.createSpecular(0.68f, 0.60f, 0.48f, 1f),
                        FloatAttribute.createShininess(92f)));
        final float topRadius = radius - bevelRadius;
        final Vector3 topCenter = new Vector3(0f, half + bevelY + 0.001f, 0f);
        for (int i = 0; i < segments; i++) {
            float a0 = MathUtils.PI2 * i / segments;
            float a1 = MathUtils.PI2 * (i + 1) / segments;
            Vector3 v0 = new Vector3(MathUtils.cos(a0) * topRadius, topCenter.y, MathUtils.sin(a0) * topRadius);
            Vector3 v1 = new Vector3(MathUtils.cos(a1) * topRadius, topCenter.y, MathUtils.sin(a1) * topRadius);
            VertexInfo c = new VertexInfo().set(topCenter, Vector3.Y, null, new Vector2(0.5f, 0.5f));
            VertexInfo a = new VertexInfo().set(v1, Vector3.Y, null,
                    new Vector2(0.5f + v1.x / (2f * topRadius), 0.5f + v1.z / (2f * topRadius)));
            VertexInfo b = new VertexInfo().set(v0, Vector3.Y, null,
                    new Vector2(0.5f + v0.x / (2f * topRadius), 0.5f + v0.z / (2f * topRadius)));
            top.triangle(c, a, b);
        }

        // The generated material is intentionally left unobstructed: no
        // oversized center medallion or concentric rings. The bevel and
        // physically varied texture provide the premium finish cleanly.
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
        MeshPartBuilder p = mb.part("point_body", GL20.GL_TRIANGLES, attrs, material);

        final float w = 0.40f;
        final float y0 = 0f;
        final float y1 = 0.115f;
        final float zBase = 1.55f;
        final float zTip = -1.55f;

        Vector3 a0 = new Vector3(-w, y0, zBase);
        Vector3 b0 = new Vector3( w, y0, zBase);
        Vector3 c0 = new Vector3(0f, y0, zTip);
        Vector3 a1 = new Vector3(-w, y1, zBase);
        Vector3 b1 = new Vector3( w, y1, zBase);
        Vector3 c1 = new Vector3(0f, y1, zTip);

        // Solid raised inlay body.
        p.triangle(c0, b0, a0);
        p.triangle(a0, b0, b1);
        p.triangle(a0, b1, a1);
        p.triangle(b0, c0, c1);
        p.triangle(b0, c1, b1);
        p.triangle(c0, a0, a1);
        p.triangle(c0, a1, c1);

        // A smaller inset cap leaves a narrow polished border around the
        // point. This gives the triangular inlay a routed, crafted edge
        // instead of a single flat polygon.
        final float insetW = 0.345f;
        final float insetBase = 1.43f;
        final float insetTip = -1.40f;
        final float insetY = y1 + 0.010f;

        VertexInfo ia = new VertexInfo().set(
                new Vector3(-insetW, insetY, insetBase), Vector3.Y, null,
                new Vector2(0.0f, 0.0f));
        VertexInfo ib = new VertexInfo().set(
                new Vector3( insetW, insetY, insetBase), Vector3.Y, null,
                new Vector2(1.0f, 0.0f));
        VertexInfo ic = new VertexInfo().set(
                new Vector3(0f, insetY, insetTip), Vector3.Y, null,
                new Vector2(0.5f, 1.0f));
        MeshPartBuilder inset = mb.part("point_inlay", GL20.GL_TRIANGLES, attrs, detailMaterial);
        inset.triangle(ia, ib, ic);

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
        float d = 0.18f;
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
        camera.fieldOfView = 30f;
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
        float top = Math.min(74f, h * 0.12f);

        uiShape.begin(ShapeRenderer.ShapeType.Filled);
        uiShape.setColor(0.035f, 0.050f, 0.055f, 0.96f);
        uiShape.rect(0f, h - top, w, top);
        uiShape.setColor(0.78f, 0.64f, 0.34f, 0.82f);
        uiShape.rect(0f, h - top, w, 2f);

        float cardW = Math.min(250f, w * 0.20f);
        float cardH = Math.min(58f, top - 10f);
        float leftCardX = 20f;
        float rightCardX = w - cardW - 20f;
        float cardY = h - top + 5f;

        uiShape.setColor(0.055f, 0.070f, 0.075f, 0.96f);
        uiShape.rect(leftCardX, cardY, cardW, cardH);
        uiShape.rect(rightCardX, cardY, cardW, cardH);

        float turnW = Math.min(280f, w * 0.25f);
        float turnX = (w - turnW) * 0.5f;
        uiShape.setColor(0.06f, 0.075f, 0.08f, 0.92f);
        uiShape.rect(turnX, cardY + 4f, turnW, cardH - 8f);
        uiShape.end();

        uiBatch.begin();
        uiFont.getData().setScale(0.88f);
        uiFont.setColor(0.88f, 0.86f, 0.79f, 1f);
        uiFont.draw(uiBatch, "EASY", leftCardX + 20f, h - 25f);
        uiFont.draw(uiBatch, "YOU", rightCardX + 20f, h - 25f);

        uiFont.getData().setScale(0.72f);
        uiFont.setColor(0.58f, 0.62f, 0.62f, 1f);
        uiFont.draw(uiBatch, "SCORE  " + lightOff, leftCardX + 20f, h - 47f);
        uiFont.draw(uiBatch, "SCORE  " + darkOff, rightCardX + 20f, h - 47f);

        uiFont.getData().setScale(0.82f);
        uiFont.setColor(0.97f, 0.92f, 0.78f, 1f);
        uiLayout.setText(uiFont, lightTurn ? "YOUR TURN" : "CPU TURN");
        uiFont.draw(uiBatch, uiLayout, turnX + turnW * 0.5f - uiLayout.width * 0.5f, cardY + cardH * 0.60f);

        uiFont.getData().setScale(0.66f);
        uiFont.setColor(0.58f, 0.61f, 0.61f, 1f);
        uiFont.draw(uiBatch, "BAR  " + lightBar + " / " + darkBar, 18f, h - top - 12f);
        uiFont.draw(uiBatch, "OFF  " + lightOff + " / " + darkOff, 105f, h - top - 12f);

        // Left-side utility controls.
        float cx = 42f;
        float[] cy = {h * 0.30f, h * 0.42f, h * 0.54f};
        String[] labels = {"≡", "⚙", "×1"};
        for (int i = 0; i < labels.length; i++) {
            uiFont.getData().setScale(i == 2 ? 0.68f : 1.05f);
            uiFont.setColor(0.90f, 0.87f, 0.98f, 1f);
            uiLayout.setText(uiFont, labels[i]);
            uiFont.draw(uiBatch, uiLayout, cx - uiLayout.width * 0.5f, cy[i] + uiLayout.height * 0.5f);
        }

        // Dice are intentionally touch-driven. No permanent Roll button:
        // tap the dice to roll, or swipe them to add throw strength.
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
                cameraDistance * (1f + amountY * 0.055f), 13.5f, 22f);
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
        if (woodGrainTexture != null) woodGrainTexture.dispose();
        if (woodNormalTexture != null) woodNormalTexture.dispose();
        if (lightCheckerTexture != null) lightCheckerTexture.dispose();
        if (lightCheckerNormalTexture != null) lightCheckerNormalTexture.dispose();
        if (darkCheckerTexture != null) darkCheckerTexture.dispose();
        if (darkCheckerNormalTexture != null) darkCheckerNormalTexture.dispose();
        if (diceTexture != null) diceTexture.dispose();
        if (diceNormalTexture != null) diceNormalTexture.dispose();
        if (studioCubemap != null) studioCubemap.dispose();
        batch.dispose();
        uiShape.dispose();
        uiBatch.dispose();
        uiFont.dispose();
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
        if (checkerShadowModel != null) checkerShadowModel.dispose();
        if (accentModel != null) accentModel.dispose();
        if (diceTrayModel != null) diceTrayModel.dispose();
        if (diceEdgeModel != null) diceEdgeModel.dispose();
        if (screwModel != null) screwModel.dispose();
        if (sideTrayModel != null) sideTrayModel.dispose();
        if (sideTrayInsetModel != null) sideTrayInsetModel.dispose();
        if (hingePlateModel != null) hingePlateModel.dispose();
        if (medallionModel != null) medallionModel.dispose();
        if (medallionRingModel != null) medallionRingModel.dispose();
        if (pointShadowModel != null) pointShadowModel.dispose();
    }
}
