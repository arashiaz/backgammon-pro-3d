package com.arashiaz.backgammonpro3d.core;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.environment.*;
import com.badlogic.gdx.graphics.g3d.utils.*;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.Array;

public final class BoardScreen extends ScreenAdapter implements InputProcessor {
    private final PerspectiveCamera camera = new PerspectiveCamera(42, 1, 1);
    private final ModelBatch batch = new ModelBatch();
    private final Environment environment = new Environment();
    private final Array<ModelInstance> models = new Array<>();
    private final ModelBuilder mb = new ModelBuilder();
    private Model boardModel, pointModel, darkChecker, lightChecker, diceModel;
    private final Vector3 drag = new Vector3();
    private ModelInstance selected;
    private float lastX, lastY;

    @Override public void show() {
        Gdx.input.setInputProcessor(this);
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.55f,0.55f,0.55f,1));
        environment.add(new DirectionalLight().set(0.9f,0.86f,0.78f,-0.45f,-1f,-0.35f));
        environment.add(new DirectionalLight().set(0.22f,0.24f,0.30f,0.5f,-0.35f,0.7f));
        buildBoard();
        camera.near = 0.1f; camera.far = 100f;
        camera.position.set(0, 18.5f, 14.5f); camera.lookAt(0,0,0); camera.up.set(0,1,0); camera.update();
    }

    private Material mat(float r,float g,float b,float roughness) {
        Material m = new Material(ColorAttribute.createDiffuse(r,g,b,1));
        m.set(ColorAttribute.createSpecular(0.35f,0.35f,0.35f,1));
        m.set(FloatAttribute.createShininess(24f * (1f-roughness) + 4f));
        return m;
    }

    private void buildBoard() {
        boardModel = mb.createBox(18f, 0.65f, 10f,
            mat(0.30f,0.13f,0.055f,0.35f), VertexAttributes.Usage.Position|VertexAttributes.Usage.Normal);
        models.add(new ModelInstance(boardModel,0,-0.35f,0));
        Model inner = mb.createBox(16.9f,0.42f,9.05f,mat(0.60f,0.31f,0.10f,0.42f),VertexAttributes.Usage.Position|VertexAttributes.Usage.Normal);
        models.add(new ModelInstance(inner,0,0.05f,0));
        pointModel = mb.createCone(1.18f,0.22f,4.0f,3,mat(0.87f,0.68f,0.42f,0.55f),VertexAttributes.Usage.Position|VertexAttributes.Usage.Normal);
        for(int i=0;i<12;i++) {
            float x=-7.25f+i*1.32f;
            ModelInstance top=new ModelInstance(pointModel,x,0.34f,-2.25f); top.transform.rotate(Vector3.X,90); models.add(top);
            ModelInstance bottom=new ModelInstance(pointModel,x,0.34f,2.25f); bottom.transform.rotate(Vector3.X,-90); models.add(bottom);
        }
        darkChecker = mb.createCylinder(1.0f,0.38f,1.0f,48,mat(0.08f,0.075f,0.085f,0.15f),VertexAttributes.Usage.Position|VertexAttributes.Usage.Normal);
        lightChecker = mb.createCylinder(1.0f,0.38f,1.0f,48,mat(0.90f,0.84f,0.70f,0.18f),VertexAttributes.Usage.Position|VertexAttributes.Usage.Normal);
        for(int i=0;i<5;i++) addChecker(lightChecker,-7.25f, -2.25f+i*0.42f);
        for(int i=0;i<5;i++) addChecker(darkChecker, 7.25f, 2.25f-i*0.42f);
        for(int i=0;i<3;i++) addChecker(darkChecker,-4.6f,2.25f-i*0.42f);
        for(int i=0;i<2;i++) addChecker(lightChecker,4.6f,-2.25f+i*0.42f);
        diceModel=mb.createBox(1.35f,1.35f,1.35f,mat(0.94f,0.94f,0.91f,0.12f),VertexAttributes.Usage.Position|VertexAttributes.Usage.Normal);
        models.add(new ModelInstance(diceModel,-2.0f,0.85f,0));
        models.add(new ModelInstance(diceModel,2.0f,0.85f,0));
    }

    private void addChecker(Model model,float x,float z){ models.add(new ModelInstance(model,x,0.48f,z)); }

    @Override public void render(float delta) {
        Gdx.gl.glViewport(0,0,Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.035f,0.045f,0.06f,1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT|GL20.GL_DEPTH_BUFFER_BIT);
        camera.update(); batch.begin(camera); for(ModelInstance m:models) batch.render(m,environment); batch.end();
    }

    @Override public boolean touchDown(int screenX,int screenY,int pointer,int button){ lastX=screenX; lastY=screenY; return true; }
    @Override public boolean touchDragged(int x,int y,int pointer){
        float dx=x-lastX, dy=y-lastY; camera.position.rotateAround(Vector3.Zero,Vector3.Y,-dx*0.16f); camera.position.y=MathUtils.clamp(camera.position.y-dy*0.035f,11f,24f); camera.lookAt(0,0,0); lastX=x; lastY=y; return true;
    }
    @Override public boolean touchUp(int x,int y,int pointer,int button){ return true; }
    @Override public boolean touchCancelled(int x,int y,int p,int b){ return true; }
    @Override public boolean touchDown(int x,int y,int p,int b,boolean unused){ return false; }
    @Override public boolean mouseMoved(int x,int y){return false;}
    @Override public boolean scrolled(float amountX,float amountY){ camera.position.scl(1f+amountY*0.05f); camera.position.y=MathUtils.clamp(camera.position.y,11f,28f); camera.lookAt(0,0,0); return true; }
    @Override public boolean keyDown(int keycode){return false;} @Override public boolean keyUp(int keycode){return false;}
    @Override public void resize(int width,int height){ camera.viewportWidth=width; camera.viewportHeight=height; camera.update(); }
    @Override public void pause(){} @Override public void resume(){}
    @Override public void hide(){}
    @Override public void dispose(){ batch.dispose(); for(ModelInstance m:models){} boardModel.dispose(); pointModel.dispose(); darkChecker.dispose(); lightChecker.dispose(); diceModel.dispose(); }
}
