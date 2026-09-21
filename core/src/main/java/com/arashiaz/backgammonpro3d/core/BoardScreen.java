package com.arashiaz.backgammonpro3d.core;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.environment.*;
import com.badlogic.gdx.graphics.g3d.utils.*;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.Array;

/**
 * Backgammon Pro 3D - polished board prototype.
 *
 * The scene is intentionally procedural so the Android build has no external
 * asset dependency yet. Geometry is kept reusable to reduce draw calls and
 * memory pressure on mobile GPUs.
 */
public final class BoardScreen extends ScreenAdapter implements InputProcessor {
    private final PerspectiveCamera camera = new PerspectiveCamera(38f, 1f, 1f);
    private final ModelBatch batch = new ModelBatch();
    private final Environment environment = new Environment();
    private final Array<ModelInstance> models = new Array<>();
    private final ModelBuilder mb = new ModelBuilder();

    private Model baseModel, playingSurfaceModel, railModel, barModel;
    private Model darkPointModel, lightPointModel;
    private Model darkChecker, lightChecker;
    private Model diceModel, dieDotModel;
    private Model accentModel;

    private float lastX, lastY;
    private boolean dragging;
    private float cameraAzimuth = 0f;
    private float cameraElevation = 48f;
    private float cameraDistance = 21.5f;

    private final Vector3 cameraTarget = new Vector3(0f, 0.25f, 0f);
    private final Vector3 tmp = new Vector3();

    @Override
    public void show() {
        Gdx.input.setInputProcessor(this);

        environment.set(new ColorAttribute(
                ColorAttribute.AmbientLight, 0.42f, 0.43f, 0.46f, 1f));
        environment.add(new DirectionalLight().set(
                1.0f, 0.91f, 0.78f, -0.55f, -1.0f, -0.35f));
        environment.add(new DirectionalLight().set(
                0.28f, 0.34f, 0.48f, 0.55f, -0.45f, 0.65f));

        buildBoard();
        updateCamera();
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

        float[] xs = {-7.05f, -5.75f, -4.45f, -3.15f, -1.85f, -0.55f,
                       0.55f,  1.85f,  3.15f,  4.45f,  5.75f,  7.05f};

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

        // Shared checker meshes. 48 radial segments gives a smooth silhouette
        // without excessive mobile geometry.
        darkChecker = mb.createCylinder(
                1.02f, 0.42f, 1.02f, 48,
                new Material(
                        ColorAttribute.createDiffuse(0.035f, 0.032f, 0.040f, 1f),
                        ColorAttribute.createSpecular(0.34f, 0.34f, 0.39f, 1f),
                        FloatAttribute.createShininess(55f)),
                attrs);

        lightChecker = mb.createCylinder(
                1.02f, 0.42f, 1.02f, 48,
                new Material(
                        ColorAttribute.createDiffuse(0.88f, 0.78f, 0.57f, 1f),
                        ColorAttribute.createSpecular(0.55f, 0.49f, 0.38f, 1f),
                        FloatAttribute.createShininess(48f)),
                attrs);

        // Standard 15 + 15 starting position.
        addStack(lightChecker, xs[0], -3.35f, 5);
        addStack(lightChecker, xs[4],  3.35f, 3);
        addStack(darkChecker,  xs[11], 3.35f, 5);
        addStack(darkChecker,  xs[7], -3.35f, 3);
        addStack(darkChecker,  xs[0],  3.35f, 2);
        addStack(lightChecker, xs[11], -3.35f, 2);
        addStack(darkChecker,  xs[4], -3.35f, 5);
        addStack(lightChecker, xs[7],  3.35f, 5);\n\n        // Dice and raised black pips.
        diceModel = mb.createBox(
                1.28f, 1.28f, 1.28f,
                new Material(
                        ColorAttribute.createDiffuse(0.94f, 0.92f, 0.84f, 1f),
                        ColorAttribute.createSpecular(0.72f, 0.68f, 0.58f, 1f),
                        FloatAttribute.createShininess(70f)),
                attrs);

        dieDotModel = mb.createCylinder(
                0.20f, 0.035f, 0.20f, 20,
                new Material(
                        ColorAttribute.createDiffuse(0.035f, 0.032f, 0.028f, 1f),
                        ColorAttribute.createSpecular(0.12f, 0.12f, 0.12f, 1f),
                        FloatAttribute.createShininess(20f)),
                attrs);

        ModelInstance dieA = new ModelInstance(diceModel, -1.55f, 1.06f, 0f);
        ModelInstance dieB = new ModelInstance(diceModel,  1.55f, 1.06f, 0f);
        dieA.transform.rotate(Vector3.Y, -9f);
        dieB.transform.rotate(Vector3.Y, 12f);
        models.add(dieA);
        models.add(dieB);
        addTopPips(-1.55f, 1.72f, 0f, 4);
        addTopPips( 1.55f, 1.72f, 0f, 2);

        // Small gold center emblem/trim.
        accentModel = mb.createCylinder(
                0.16f, 0.035f, 0.16f, 32,
                wood(0.75f, 0.48f, 0.16f, 45f), attrs);
        models.add(new ModelInstance(accentModel, 0f, 0.68f, 0f));
    }

    private Model createPointModel(Material material, long attrs, boolean unused) {
        mb.begin();
        MeshPartBuilder p = mb.part("point", GL20.GL_TRIANGLES, attrs, material);

        final float w = 0.54f;
        final float y0 = 0f;
        final float y1 = 0.12f;
        final float zBase = 2.0f;
        final float zTip = -2.0f;

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
        float d = 0.31f;
        if (number == 1) {
            addPip(x, y, z);
        } else if (number == 2) {
            addPip(x - d, y, z - d);
            addPip(x + d, y, z + d);
        } else if (number == 4) {
            addPip(x - d, y, z - d);
            addPip(x + d, y, z - d);
            addPip(x - d, y, z + d);
            addPip(x + d, y, z + d);
        }
    }

    private void addPip(float x, float y, float z) {
        models.add(new ModelInstance(dieDotModel, x, y, z));
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
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.012f, 0.015f, 0.020f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        camera.update();
        batch.begin(camera);
        for (ModelInstance model : models) {
            batch.render(model, environment);
        }
        batch.end();
    }

    @Override
    public boolean touchDown(int x, int y, int pointer, int button) {
        lastX = x;
        lastY = y;
        dragging = true;
        return true;
    }

    @Override
    public boolean touchDragged(int x, int y, int pointer) {
        if (!dragging) return true;

        float dx = x - lastX;
        float dy = y - lastY;

        cameraAzimuth = MathUtils.clamp(cameraAzimuth - dx * 0.18f, -28f, 28f);
        cameraElevation = MathUtils.clamp(cameraElevation - dy * 0.12f, 35f, 64f);
        updateCamera();

        lastX = x;
        lastY = y;
        return true;
    }

    @Override
    public boolean touchUp(int x, int y, int pointer, int button) {
        dragging = false;
        return true;
    }

    @Override
    public boolean touchCancelled(int x, int y, int pointer, int button) {
        dragging = false;
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
