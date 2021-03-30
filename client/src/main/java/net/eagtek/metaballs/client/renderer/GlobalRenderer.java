package net.eagtek.metaballs.client.renderer;

import static org.lwjgl.opengles.GLES30.*;

import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengles.EXTTextureFilterAnisotropic;
import org.lwjgl.system.MemoryStack;

import net.eagtek.eagl.EaglFramebuffer;
import net.eagtek.eagl.EaglFramebuffer.DepthBufferType;
import net.eagtek.eagl.EaglImage2D;
import net.eagtek.eagl.EaglModelLoader;
import net.eagtek.eagl.EaglProgram;
import net.eagtek.eagl.EaglTessellator;
import net.eagtek.eagl.EaglVertexArray;
import net.eagtek.eagl.EaglVertexBuffer;
import net.eagtek.eagl.GLDataType;
import net.eagtek.eagl.GLStateManager;
import net.eagtek.eagl.ResourceLoader;
import net.eagtek.metaballs.MathUtil;
import net.eagtek.metaballs.client.GameClient;
import net.eagtek.metaballs.client.GameConfiguration;
import net.eagtek.metaballs.client.renderer.LightData.LightType;

public class GlobalRenderer {
	
	public final GameClient client;

	public final Matrix4fStack modelMatrix = new Matrix4fStack(64);
	public final Matrix4fStack cameraMatrix = new Matrix4fStack(4);
	public final Matrix4fStack projMatrix = new Matrix4fStack(4);
	public final Matrix4fStack viewProjMatrix = new Matrix4fStack(4);
	public final Matrix4f multipliedMatrix = new Matrix4f();

	public final Matrix4f sunShadowProjViewA = new Matrix4f();
	public final Matrix4f sunShadowProjViewB = new Matrix4f();
	public final Matrix4f sunShadowProjViewC = new Matrix4f();
	public final Matrix4f sunShadowProjViewD = new Matrix4f();
	
	public FrustumIntersection viewProjFustrum = new FrustumIntersection();
	
	public final ProgramManager progManager;
	
	private final EaglVertexArray quadArray;
	
	private final EaglImage2D testGraphic;
	private final EaglImage2D bananaTexture;
	private final EaglImage2D dirtTexture;
	private final EaglImage2D testModelTexture;
	
	private ModelObjectRenderer testModelRenderer = null;
	private ModelObjectRenderer longArmsRenderer = null;
	private ModelObjectRenderer bananaRenderer = null;
	private ModelObjectRenderer bananaRenderer2 = null;

	private EaglVertexArray lightSphere = null;
	private EaglVertexArray lightHemisphere = null;
	private EaglVertexArray lightCone = null;
	
	private EaglVertexArray skyDome = null;
	
	private ShadowLightRenderer lightTest = null;

	private final EaglFramebuffer gBuffer;
	private final EaglFramebuffer lightBuffer;
	private final EaglFramebuffer combinedBuffer;

	private final EaglFramebuffer sunShadowMap;
	private final EaglFramebuffer sunShadowBuffer;
	
	private final EaglFramebuffer lightShadowMap;

	private final EaglFramebuffer linearDepthBuffer;
	private final EaglFramebuffer ambientOcclusionBuffer;
	private final EaglFramebuffer ambientOcclusionBlur;

	private final EaglFramebuffer skyBuffer;
	
	private final EaglFramebuffer postBufferA;
	private final EaglFramebuffer postBufferB;
	private final EaglFramebuffer postBufferC;
	private final EaglFramebuffer exposureCalcTexture;
	
	private final EaglFramebuffer toneMapped;

	private float exposure = 2.0f;
	private float targetExposure = 2.0f;
	
	private long secondTimer = 0l;
	private int framesPassed = 0;
	private int prevFramesPassed = 0;
	
	private int frameCounterTotal = 0;

	public double renderPosX;
	public double renderPosY;
	public double renderPosZ;

	public final ColorTemperature colorTemperatures;
	
	private final Random rand;
	private float grainStartRandom = 0.0f;
	private float grainEndRandom = 0.0f;
	
	private boolean nextTick = true;
	
	public int getFramerate() {
		return prevFramesPassed;
	}
	
	public GlobalRenderer(GameClient gameClient) {
		client = gameClient;
		progManager = new ProgramManager(this);

		for(int i = 0; i < bbVertexes.length; ++i) {
			bbVertexes[i] = new Vector4f();
		}
		
		// setup test quad =====================================================
		
		EaglTessellator t = new EaglTessellator(20, 6, 0);
		EaglVertexBuffer vbo = new EaglVertexBuffer();
		quadArray = new EaglVertexArray(
			new EaglVertexBuffer[] { vbo }, new EaglVertexArray.VertexAttribPointer[] {
			EaglVertexArray.attrib(0, 0, 3, GLDataType.FLOAT, false, 20, 0), EaglVertexArray.attrib(0, 1, 2, GLDataType.FLOAT, false, 20, 12)
		});
		t.put_vec3f(-1.0f, -1.0f, 0.0f).put_vec2f(0.0f, 0.0f).endVertex();
		t.put_vec3f( 1.0f, -1.0f, 0.0f).put_vec2f(1.0f, 0.0f).endVertex();
		t.put_vec3f( 1.0f,  1.0f, 0.0f).put_vec2f(1.0f, 1.0f).endVertex();
		t.put_vec3f(-1.0f,  1.0f, 0.0f).put_vec2f(0.0f, 1.0f).endVertex();
		t.put_vec3f(-1.0f, -1.0f, 0.0f).put_vec2f(0.0f, 0.0f).endVertex();
		t.put_vec3f( 1.0f,  1.0f, 0.0f).put_vec2f(1.0f, 1.0f).endVertex();
		t.put_vec3f(-1.0f,  1.0f, 0.0f).put_vec2f(0.0f, 1.0f).endVertex();
		t.uploadVertexes(vbo, true);
		t.destroy();
		
		//setup test texture ==================================================
		
		testGraphic = EaglImage2D.consumeStream(ResourceLoader.loadResource("metaballs/icon64.png"));
		testModelTexture = EaglImage2D.consumeStream(ResourceLoader.loadResource("metaballs/textures/longarms_texture.png"));
		bananaTexture = EaglImage2D.consumeStream(ResourceLoader.loadResource("metaballs/textures/banana_texture.png"));
		dirtTexture = EaglImage2D.consumeStream(ResourceLoader.loadResource("metaballs/textures/dirt1.jpg"));
		
		dirtTexture.generateMipmap().filter(GL_LINEAR_MIPMAP_NEAREST, GL_LINEAR, EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
		
		//load test model =====================================================
		
		try {
			InputStream stream;

			stream = ResourceLoader.loadResource("metaballs/models/testscene.mdl");
			client.getScene().objectRenderers.add(testModelRenderer = new ModelObjectRenderer(EaglModelLoader.loadModel(stream), testModelTexture.glObject, ModelObjectRenderer.passes_all_opaque));
			stream.close();
			
			stream = ResourceLoader.loadResource("metaballs/models/longarms.mdl");
			client.getScene().objectRenderers.add(longArmsRenderer = new ModelObjectRenderer(EaglModelLoader.loadModel(stream), testModelTexture.glObject, ModelObjectRenderer.passes_all_opaque));
			stream.close();
			
			stream = ResourceLoader.loadResource("metaballs/models/banana.mdl");
			client.getScene().objectRenderers.add(bananaRenderer = new ModelObjectRenderer(EaglModelLoader.loadModel(stream), bananaTexture.glObject, ModelObjectRenderer.passes_all_opaque));
			client.getScene().objectRenderers.add(bananaRenderer2 = new ModelObjectRenderer(bananaRenderer.array, bananaTexture.glObject, ModelObjectRenderer.passes_all_opaque));
			stream.close();
			
			stream = ResourceLoader.loadResource("metaballs/models/lightcone.mdl");
			lightCone = EaglModelLoader.loadModel(stream);
			stream.close();
			
			stream = ResourceLoader.loadResource("metaballs/models/lightsphere.mdl");
			lightSphere = EaglModelLoader.loadModel(stream);
			stream.close();
			
			stream = ResourceLoader.loadResource("metaballs/models/lighthemisphere.mdl");
			lightHemisphere = EaglModelLoader.loadModel(stream);
			stream.close();
			
			stream = ResourceLoader.loadResource("metaballs/models/skydome.mdl");
			skyDome = EaglModelLoader.loadModel(stream);
			stream.close();
			
		}catch(Throwable tt) {
			throw new RuntimeException("Could not load model files required for rendering", tt);
		}
		
		//load color temp table =====================================================
		
		colorTemperatures = new ColorTemperature(ResourceLoader.loadResourceBytes("metaballs/temperatures.lut"));
		
		client.getScene().sunDirection = new Vector3f(1.0f, -1.0f, 0.0f).normalize();
		//client.getScene().lightRenderers.add(new LightData(LightType.POINT, 5.0f, 0.0f, 0.0d, 1.0d, 0.0d));
		
		this.rand = new Random();
		
		for(int i = 0 ; i < 35; ++i) {
			client.getScene().lightRenderers.add(new LightData(LightType.POINT, rand.nextInt(200), 0.0f, rand.nextGaussian() * 20.0d, rand.nextGaussian() * 3.0d + 4.0d, rand.nextGaussian() * 20.0d).setRGB(rand.nextFloat(), rand.nextFloat(), rand.nextFloat()).setDirection(0.0f, 1.0f, 0.0f));
		}
		
		client.getScene().shadowLightRenderers.add(lightTest = (ShadowLightRenderer) new ShadowLightRenderer(LightType.SPOT, 100.0f, 0.2f, 0.0d, 5.0d, 0.0d).setRGB(1.0f, 1.0f, 1.0f).setDirection(-1.0f, -1.0f, 0.0f).setSpotRadius(20.0f));
		
		//setup framebuffer ==================================================
		
		//gbuffer render targets
		// 0 - diffuseRGB, ditherBlend
		// 1 - metallic, roughness, specular, ssr
		// 2 - normalXYZ, emission
		// 3 - position

		gBuffer = new EaglFramebuffer(DepthBufferType.DEPTH24_STENCIL8_TEXTURE, GL_RGBA8, GL_RGBA8, GL_RGBA8, GL_RGB16F);
		lightBuffer = new EaglFramebuffer(gBuffer.depthBuffer, GL_RGB16F, GL_RGB16F);

		combinedBuffer = new EaglFramebuffer(gBuffer.depthBuffer, GL_RGB16F);

		sunShadowMap = new EaglFramebuffer(DepthBufferType.DEPTH24_TEXTURE);
		sunShadowBuffer = new EaglFramebuffer(gBuffer.depthBuffer, GL_R8);
		
		lightShadowMap = new EaglFramebuffer(DepthBufferType.DEPTH24_TEXTURE);
		
		linearDepthBuffer = new EaglFramebuffer(DepthBufferType.NONE, GL_R32F);
		ambientOcclusionBuffer = new EaglFramebuffer(DepthBufferType.NONE, GL_R8);
		ambientOcclusionBlur = new EaglFramebuffer(DepthBufferType.NONE, GL_R8);
		
		skyBuffer = new EaglFramebuffer(DepthBufferType.NONE, GL_RGB16F);

		postBufferA = new EaglFramebuffer(DepthBufferType.NONE, GL_RGB16F);
		postBufferB = new EaglFramebuffer(DepthBufferType.NONE, GL_RGB16F);
		postBufferC = new EaglFramebuffer(DepthBufferType.NONE, GL_RGB16F);

		toneMapped = new EaglFramebuffer(DepthBufferType.NONE, GL_RGB);
		exposureCalcTexture = new EaglFramebuffer(DepthBufferType.NONE, GL_R32F);
		
	}

	public static final Vector3f up = new Vector3f(0.0f, 0.0f, 1.0f);
	public static final Vector3f up2 = new Vector3f(0.0f, 1.0f, 0.0f);
	public static final Matrix4f matrixIdentity = new Matrix4f().identity();
	
	private static final float lerp(float a, float b, float f){
	    return a + f * (b - a);
	}
	
	public void renderGame(RenderScene scene) {
		
		renderPosX = client.prevRenderX + (client.renderX - client.prevRenderX) * client.partialTicks;
		renderPosY = client.prevRenderY + (client.renderY - client.prevRenderY) * client.partialTicks;
		renderPosZ = client.prevRenderZ + (client.renderZ - client.prevRenderZ) * client.partialTicks;
		
		Vector3f sd = scene.sunDirection;
		sd.set(0.0f, 1.0f, 0.0f).normalize();
		//sd.rotateZ(120.0f * MathUtil.toRadians);
		sd.rotateZ((100.0f - ((client.totalTicksF * 0.025f) % 180.0f)) * MathUtil.toRadians);
		sd.rotateY(20.0f * MathUtil.toRadians);
		
		float timeOfDay = Math.max(sd.dot(0.0f, 1.0f, 0.0f), 0.0f);
		
		scene.skyBrightness = timeOfDay * 2.0f;
		scene.sunBrightness = 100.0f;
		
		scene.sunSize = 0.1f;
		
		scene.sunKelvin = (int) lerp(1000.0f, 4000.0f, Math.min(timeOfDay, 1.0f));
		
		scene.fogKelvin = 6000;
		
		scene.fogDensity = 0.01f;
		
		int w = client.context.getInnerWidth();
		int h = client.context.getInnerHeight();

		// ================================================= RENDER THE G BUFFER =======================================================
		
		gBuffer.setSize(w, h);
		gBuffer.bindFramebuffer();
		
		glViewport(0, 0, w, h);

		glStencilMask(0xFF);
		glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
		glClearDepthf(0.0f);
		glClearStencil(0x00);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

		glEnable(GL_STENCIL_TEST);
		glStencilMask(0xFF);
		glStencilFunc(GL_ALWAYS, 0xFF, 0xFF);
		glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);
		
		glEnable(GL_DEPTH_TEST);
		glDepthFunc(GL_GREATER);
		glDepthMask(true);
		
		glEnable(GL_CULL_FACE);
		glCullFace(GL_BACK);
		
		projMatrix.identity().scale(1.0f, 1.0f, -1.0f).perspective(100.0f * MathUtil.toRadians, (float)w / (float)h, 0.1f, GameConfiguration.farPlane);
		cameraMatrix.identity()
		.rotate(-(client.prevRenderPitch + (client.renderPitch - client.prevRenderPitch) * client.partialTicks) * MathUtil.toRadians, 1.0f, 0.0f, 0.0f)
		.rotate(-(client.prevRenderYaw + (client.renderYaw - client.prevRenderYaw) * client.partialTicks) * MathUtil.toRadians, 0.0f, 1.0f, 0.0f);
		
		cameraMatrix.mulLocal(projMatrix, viewProjMatrix);
		viewProjFustrum.set(viewProjMatrix);
		
		modelMatrix.clear();
		
		testModelRenderer.setMaterial(0.0f, 0.0f, 0.5f, 0.2f, 0.0f, 0.0f);
		
		longArmsRenderer.setMaterial(0.0f, 0.0f, 0.7f, 0.1f, 0.0f, 0.0f);
		longArmsRenderer.setPosition(0.0d, 0.0d, 0.0d).setRotation(0.0f, (client.totalTicksF * 2f) % 360.0f, 0.0f);
		
		bananaRenderer.setMaterial(0.0f, 0.0f, 0.6f, 0.2f, 0.0f, 0.0f);
		bananaRenderer.setPosition(3.0d, 0.2d, -5.0d).setRotation(0.0f, -90.0f, 0.0f);
		
		bananaRenderer2.setMaterial(0.0f, 0.0f, 0.6f, 0.2f, 0.0f, 0.0f);
		bananaRenderer2.setPosition(-22.0d, 4.0d, 13.0d).setRotation(150.0f, -160.0f, -75.0f).setScale(5.0f);
		
		Iterator<ObjectRenderer> objectRenderers = scene.objectRenderers.iterator();
		while(objectRenderers.hasNext()) {
			ObjectRenderer r = objectRenderers.next();
			if(r.shouldRenderPass(RenderPass.G_BUFFER)) {
				r.renderPass(RenderPass.G_BUFFER, this);
			}
		}
		
		glDisable(GL_STENCIL_TEST);
		glEnable(GL_DEPTH_TEST);
		glDepthFunc(GL_GREATER);
		glEnable(GL_CULL_FACE);
		glCullFace(GL_FRONT);
		glDepthMask(true);

		// ================================================= RENDER SUN SHADOW MAPS =======================================================
		
		sunShadowMap.setSize(GameConfiguration.sunShadowMapResolution * 4, GameConfiguration.sunShadowMapResolution);
		sunShadowMap.bindFramebuffer();
		glViewport(0, 0, GameConfiguration.sunShadowMapResolution, GameConfiguration.sunShadowMapResolution);

		projMatrix.pushMatrix();
		cameraMatrix.pushMatrix();
		viewProjMatrix.pushMatrix();
		modelMatrix.pushMatrix();
		modelMatrix.identity();
		
		cameraMatrix.identity().translate(0f, 0f, -GameConfiguration.sunShadowDistance).lookAlong(scene.sunDirection.mul(-1.0f, up2), up);
		
		projMatrix.identity().scale(1.0f, 1.0f, -1.0f).ortho(
				-GameConfiguration.sunShadowLODADistance, 
				GameConfiguration.sunShadowLODADistance, 
				-GameConfiguration.sunShadowLODADistance, 
				GameConfiguration.sunShadowLODADistance, 
				0f, 
				GameConfiguration.sunShadowDistance * 2.0f
		);
		cameraMatrix.mulLocal(projMatrix, viewProjMatrix);
		sunShadowProjViewA.set(viewProjMatrix);
		viewProjFustrum.set(viewProjMatrix);
		
		glClearDepthf(0.0f);
		glClear(GL_DEPTH_BUFFER_BIT);
		
		objectRenderers = scene.objectRenderers.iterator();
		while(objectRenderers.hasNext()) {
			ObjectRenderer r = objectRenderers.next();
			if(r.shouldRenderPass(RenderPass.SHADOW_A)) {
				r.renderPass(RenderPass.SHADOW_A, this);
			}
		}
		
		glViewport(GameConfiguration.sunShadowMapResolution * 1, 0, GameConfiguration.sunShadowMapResolution, GameConfiguration.sunShadowMapResolution);
		
		projMatrix.identity().scale(1.0f, 1.0f, -1.0f).ortho(
				-GameConfiguration.sunShadowLODBDistance, 
				GameConfiguration.sunShadowLODBDistance, 
				-GameConfiguration.sunShadowLODBDistance, 
				GameConfiguration.sunShadowLODBDistance, 
				0f, 
				GameConfiguration.sunShadowDistance * 2.0f
		);
		cameraMatrix.mulLocal(projMatrix, viewProjMatrix);
		sunShadowProjViewB.set(viewProjMatrix);
		viewProjFustrum.set(viewProjMatrix);
		
		objectRenderers = scene.objectRenderers.iterator();
		while(objectRenderers.hasNext()) {
			ObjectRenderer r = objectRenderers.next();
			if(r.shouldRenderPass(RenderPass.SHADOW_B)) {
				r.renderPass(RenderPass.SHADOW_B, this);
			}
		}
		
		glViewport(GameConfiguration.sunShadowMapResolution * 2, 0, GameConfiguration.sunShadowMapResolution, GameConfiguration.sunShadowMapResolution);
		
		projMatrix.identity().scale(1.0f, 1.0f, -1.0f).ortho(
				-GameConfiguration.sunShadowLODCDistance, 
				GameConfiguration.sunShadowLODCDistance, 
				-GameConfiguration.sunShadowLODCDistance, 
				GameConfiguration.sunShadowLODCDistance, 
				0f, 
				GameConfiguration.sunShadowDistance * 2.0f
		);
		cameraMatrix.mulLocal(projMatrix, viewProjMatrix);
		sunShadowProjViewC.set(viewProjMatrix);
		viewProjFustrum.set(viewProjMatrix);
		
		objectRenderers = scene.objectRenderers.iterator();
		while(objectRenderers.hasNext()) {
			ObjectRenderer r = objectRenderers.next();
			if(r.shouldRenderPass(RenderPass.SHADOW_C)) {
				r.renderPass(RenderPass.SHADOW_C, this);
			}
		}
		
		glViewport(GameConfiguration.sunShadowMapResolution * 3, 0, GameConfiguration.sunShadowMapResolution, GameConfiguration.sunShadowMapResolution);
		
		projMatrix.identity().scale(1.0f, 1.0f, -1.0f).ortho(
				-GameConfiguration.sunShadowLODDDistance, 
				GameConfiguration.sunShadowLODDDistance, 
				-GameConfiguration.sunShadowLODDDistance, 
				GameConfiguration.sunShadowLODDDistance,
				0f,
				GameConfiguration.sunShadowDistance * 2.0f
		);
		cameraMatrix.mulLocal(projMatrix, viewProjMatrix);
		sunShadowProjViewD.set(viewProjMatrix);
		viewProjFustrum.set(viewProjMatrix);
		
		objectRenderers = scene.objectRenderers.iterator();
		while(objectRenderers.hasNext()) {
			ObjectRenderer r = objectRenderers.next();
			if(r.shouldRenderPass(RenderPass.SHADOW_D)) {
				r.renderPass(RenderPass.SHADOW_D, this);
			}
		}
		
		// ================================================= RENDER LIGHT SHADOW MAPS =======================================================

		lightShadowMap.setSize(GameConfiguration.lightShadowMapResolution * 6, GameConfiguration.lightShadowMapResolution * 6);
		lightShadowMap.bindFramebuffer();
		
		glClearDepthf(0.0f);
		glClear(GL_DEPTH_BUFFER_BIT);
		
		int atlasLocation = 0;
		
		lightTest.setDirection(-1.0f, -1.0f, -0.5f).setSpotRadius(50.0f);
		lightTest.pointsize = 30.0f;

		lightTest.lightX = 3.0d;
		lightTest.lightY = 6.0d;
		lightTest.lightZ = 1.0d;
		lightTest.emission = 200.0f;
		
		viewProjMatrix.popMatrix();
		viewProjFustrum.set(viewProjMatrix);
		viewProjMatrix.pushMatrix();
		
		double oldRPX = renderPosX;
		double oldRPY = renderPosY;
		double oldRPZ = renderPosZ;
		
		Iterator<ShadowLightRenderer> shadowLightRenderers = scene.shadowLightRenderers.iterator();
		FrustumIntersection i = new FrustumIntersection();
		FrustumIntersection old = viewProjFustrum;
		while(shadowLightRenderers.hasNext()) {
			ShadowLightRenderer s = shadowLightRenderers.next();
			float x = (float)(s.lightX - oldRPX);
			float y = (float)(s.lightY - oldRPY);
			float z = (float)(s.lightZ - oldRPZ);
			renderPosX = s.lightX;
			renderPosY = s.lightY;
			renderPosZ = s.lightZ;
			float lightRadius = (float)Math.sqrt(s.emission) * 2.0f;
			if(viewProjFustrum.testSphere(x, y, z, lightRadius) && atlasLocation < 36) {
				s.objectsInFrustum = new LinkedList();
				cameraMatrix.identity().lookAlong(s.direction, up);
				projMatrix.identity().scale(1.0f, 1.0f, -1.0f).perspective(Math.min(s.spotRadius * MathUtil.toRadians * 2.25f, 50.0f * MathUtil.toRadians * 2.25f), 1.0f, 0.1f, lightRadius);
				cameraMatrix.mulLocal(projMatrix, viewProjMatrix);
				s.shadowMatrix.set(viewProjMatrix);
				i.set(viewProjMatrix);
				viewProjFustrum = i;
				objectRenderers = scene.objectRenderers.iterator();
				while(objectRenderers.hasNext()) {
					ObjectRenderer r = objectRenderers.next();
					if(r.shouldRenderPass(RenderPass.LIGHT_SHADOW) && r.isInFrustum(this)) {
						s.objectsInFrustum.add(r);
					}
				}
				if(s.objectsInFrustum.size() > 0 && atlasLocation < 36) {
					s.atlasLocation = atlasLocation++;
					int xx = s.atlasLocation % 6;
					int yy = s.atlasLocation / 6;
					glViewport(xx * GameConfiguration.lightShadowMapResolution, yy * GameConfiguration.lightShadowMapResolution, GameConfiguration.lightShadowMapResolution, GameConfiguration.lightShadowMapResolution);
					objectRenderers = s.objectsInFrustum.iterator();
					while(objectRenderers.hasNext()) {
						ObjectRenderer r = objectRenderers.next();
						r.renderPass(RenderPass.LIGHT_SHADOW, this);
					}
				}
				viewProjFustrum = old;
			}
		}
		
		renderPosX = oldRPX;
		renderPosY = oldRPY;
		renderPosZ = oldRPZ;
		
		projMatrix.popMatrix();
		cameraMatrix.popMatrix();
		viewProjMatrix.popMatrix();
		modelMatrix.popMatrix();
		
		viewProjFustrum.set(viewProjMatrix);

		// ================================================= RENDER SUN SHADOW BUFFER =======================================================
		
		sunShadowBuffer.setSize(w, h);
		sunShadowBuffer.bindFramebuffer();
		
		glEnable(GL_STENCIL_TEST);
		glStencilMask(0x0);
		glStencilFunc(GL_NOTEQUAL, 0, 0xFF);
		glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
		
		glViewport(0, 0, w, h);
		glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
		glClear(GL_COLOR_BUFFER_BIT);
		glDisable(GL_DEPTH_TEST);
		glDisable(GL_CULL_FACE);
		glDepthMask(false);
		
		progManager.sunshadow_generate.use();
		progManager.sunshadow_generate_matrixA.setMatrix4f(sunShadowProjViewA);
		progManager.sunshadow_generate_matrixB.setMatrix4f(sunShadowProjViewB);
		progManager.sunshadow_generate_matrixC.setMatrix4f(sunShadowProjViewC);
		progManager.sunshadow_generate_matrixD.setMatrix4f(sunShadowProjViewD);
		progManager.sunshadow_generate_randTimer.set1f(client.totalTicksF % 100.0f);
		progManager.sunshadow_generate_softShadow.set1i(GameConfiguration.enableSoftShadows ? 1 : 0);

		gBuffer.bindColorTexture(2, 0);
		gBuffer.bindColorTexture(3, 1);
		sunShadowMap.bindDepthTexture(2);
		quadArray.draw(GL_TRIANGLES, 0, 6);
		
		glDisable(GL_STENCIL_TEST);
		
		// ================================================= RENDER LINEAR DEPTH BUFFER =======================================================

		linearDepthBuffer.setSize(w, h);
		linearDepthBuffer.bindFramebuffer();
		
		glViewport(0, 0, w, h);
		glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
		glClear(GL_COLOR_BUFFER_BIT);
		glDisable(GL_DEPTH_TEST);
		glDisable(GL_CULL_FACE);
		glDepthMask(false);
		
		progManager.linearize_depth.use();
		progManager.linearize_depth_farPlane.set1f(GameConfiguration.farPlane);
		gBuffer.bindDepthTexture();
		quadArray.draw(GL_TRIANGLES, 0, 6);
		
		if(GameConfiguration.enableAmbientOcclusion) {
			// ================================================= RENDER AMBIENT OCCLUSION =======================================================
			
			ambientOcclusionBuffer.setSize(w / 2, h / 2);
			ambientOcclusionBuffer.bindFramebuffer();
	
			glViewport(0, 0, w / 2, h / 2);
			glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
			glClear(GL_COLOR_BUFFER_BIT);
			glDisable(GL_DEPTH_TEST);
			glDisable(GL_CULL_FACE);
			glDepthMask(false);
			
			progManager.ssao_generate.use();
			progManager.ssao_generate_randomTime.set1f(client.totalTicksF);
			progManager.ssao_generate_matrix_p_inv.setMatrix4f(projMatrix.invert(multipliedMatrix));
			progManager.ssao_generate_matrix_v_invtrans.setMatrix4f(cameraMatrix.invert(multipliedMatrix).transpose());
			updateMatrix(progManager.ssao_generate);
			gBuffer.bindDepthTexture(1);
			gBuffer.bindColorTexture(2, 0);
			quadArray.draw(GL_TRIANGLES, 0, 6);
	
			// ================================================= BLUR HORIZONTAL OCCLUSION =======================================================
			
			ambientOcclusionBlur.setSize(w / 2, h / 2);
			ambientOcclusionBlur.bindFramebuffer();
			
			progManager.ssao_blur.use();
			progManager.ssao_blur_blurDirection.set2f(4.0f / w, 0.0f);
			updateMatrix(progManager.ssao_blur);
			linearDepthBuffer.bindColorTexture(0, 1);
			//gBuffer.bindDepthTexture(1);
			ambientOcclusionBuffer.bindColorTexture(0, 0);
			quadArray.draw(GL_TRIANGLES, 0, 6);
	
			// ================================================= BLUR VERTICAL OCCLUSION =======================================================
			
			ambientOcclusionBuffer.setSize(w / 2, h / 2);
			ambientOcclusionBuffer.bindFramebuffer();
			
			progManager.ssao_blur.use();
			progManager.ssao_blur_blurDirection.set2f(0.0f, 4.0f / h);
			updateMatrix(progManager.ssao_blur);
			ambientOcclusionBlur.bindColorTexture(0, 0);
			linearDepthBuffer.bindColorTexture(0, 1);
			//gBuffer.bindDepthTexture(1);
			quadArray.draw(GL_TRIANGLES, 0, 6);
		}else {
			ambientOcclusionBuffer.setSize(w / 2, h / 2);
			ambientOcclusionBuffer.bindFramebuffer();
			glViewport(0, 0, w / 2, h / 2);
			glClearColor(1.0f, 0.0f, 0.0f, 0.0f);
			glClear(GL_COLOR_BUFFER_BIT);
		}
		
		// ================================================= RENDER LIGHT SOURCE DIFFUSE AND SPECULAR =======================================================
		
		lightBuffer.setSize(w, h);
		lightBuffer.bindFramebuffer();
		
		glViewport(0, 0, w, h);
		glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
		glClear(GL_COLOR_BUFFER_BIT);
		
		glEnable(GL_STENCIL_TEST);
		glStencilMask(0x0);
		glStencilFunc(GL_NOTEQUAL, 0, 0xFF);
		glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
		
		glEnable(GL_BLEND);
		glBlendFunc(GL_ONE, GL_ONE);
		
		gBuffer.bindColorTexture(1, 0);
		gBuffer.bindColorTexture(2, 1);
		gBuffer.bindColorTexture(3, 2);

		glEnable(GL_CULL_FACE);
		glCullFace(GL_FRONT);
		
		Iterator<LightData> lightRenderers = scene.lightRenderers.iterator();
		while(lightRenderers.hasNext()) {
			LightData r = lightRenderers.next();
			float x = (float)(r.lightX - renderPosX);
			float y = (float)(r.lightY - renderPosY);
			float z = (float)(r.lightZ - renderPosZ);
			float lightRadius = (float)Math.sqrt(r.emission) * 2.0f;
			if(viewProjFustrum.testSphere(x, y, z, lightRadius)) {
				if(r.type == LightType.POINT) {
					modelMatrix.pushMatrix();
					translateToWorldCoords(r.lightX, r.lightY, r.lightZ);
					modelMatrix.scale(lightRadius);
					progManager.light_point.use();
					progManager.light_point_lightColor.set3f(r.lightR, r.lightG, r.lightB);
					progManager.light_point_lightPosition.set3f(x, y, z);
					progManager.light_point_screenSize.set2f(w, h);
					progManager.light_point_emission.set1f(r.emission);
					progManager.light_point_size.set1f(r.pointsize);
					updateMatrix(progManager.light_point);
					lightSphere.drawAll(GL_TRIANGLES);
					modelMatrix.popMatrix();
				}else if(r.type == LightType.SPOT) {
					modelMatrix.pushMatrix();
					translateToWorldCoords(r.lightX, r.lightY, r.lightZ);
					modelMatrix.rotateTowards(r.direction, up);
					modelMatrix.rotate(-90.0f * MathUtil.toRadians, 1.0f, 0.0f, 0.0f);
					modelMatrix.scale(1.0f, lightRadius, 1.0f);
					float spotRadius2 = (float)Math.sqrt(r.spotRadius * 5.0f);
					modelMatrix.scale(spotRadius2, 1.0f, spotRadius2);
					progManager.light_spot.use();
					progManager.light_spot_lightColor.set3f(r.lightR, r.lightG, r.lightB);
					progManager.light_spot_lightDirection.set3f(r.direction.x, r.direction.y, r.direction.z);
					progManager.light_spot_lightPosition.set3f(x, y, z);
					progManager.light_spot_screenSize.set2f(w, h);
					progManager.light_spot_radius.set1f(r.spotRadius / 180.0f);
					progManager.light_spot_emission.set1f(r.emission);
					progManager.light_spot_size.set1f(r.pointsize);
					updateMatrix(progManager.light_spot);
					lightCone.drawAll(GL_TRIANGLES);
					modelMatrix.popMatrix();
				}
			}
		}

		lightShadowMap.bindDepthTexture(3);
		
		shadowLightRenderers = scene.shadowLightRenderers.iterator();
		while(shadowLightRenderers.hasNext()) {
			ShadowLightRenderer s = shadowLightRenderers.next();
			if(s.objectsInFrustum != null && s.objectsInFrustum.size() > 0) {
				LightData r = s;
				float x = (float)(r.lightX - renderPosX);
				float y = (float)(r.lightY - renderPosY);
				float z = (float)(r.lightZ - renderPosZ);
				float lightRadius = (float)Math.sqrt(r.emission) * 2.0f;
				if(viewProjFustrum.testSphere(x, y, z, lightRadius)) {
					if(r.type == LightType.POINT) {
						modelMatrix.pushMatrix();
						translateToWorldCoords(r.lightX, r.lightY, r.lightZ);
						modelMatrix.scale(lightRadius);
						progManager.light_point_shadowmap.use();
						progManager.light_point_shadowmap_lightColor.set3f(r.lightR, r.lightG, r.lightB);
						progManager.light_point_shadowmap_lightPosition.set3f(x, y, z);
						progManager.light_point_shadowmap_screenSize.set2f(w, h);
						progManager.light_point_shadowmap_emission.set1f(r.emission);
						progManager.light_point_shadowmap_size.set1f(r.pointsize);
						progManager.light_point_shadowmap_shadowMatrix.setMatrix4f(s.shadowMatrix);
						progManager.light_point_shadowmap_shadowMapIndex.set1f(s.atlasLocation);
						updateMatrix(progManager.light_point_shadowmap);
						lightSphere.drawAll(GL_TRIANGLES);
						modelMatrix.popMatrix();
					}else if(r.type == LightType.SPOT) {
						modelMatrix.pushMatrix();
						translateToWorldCoords(r.lightX, r.lightY, r.lightZ);
						modelMatrix.rotateTowards(r.direction, up);
						modelMatrix.rotate(-90.0f * MathUtil.toRadians, 1.0f, 0.0f, 0.0f);
						modelMatrix.scale(1.0f, lightRadius, 1.0f);
						float spotRadius2 = (float)Math.sqrt(r.spotRadius * 5.0f);
						modelMatrix.scale(spotRadius2, 1.0f, spotRadius2);
						progManager.light_spot_shadowmap.use();
						progManager.light_spot_shadowmap_lightColor.set3f(r.lightR, r.lightG, r.lightB);
						progManager.light_spot_shadowmap_lightDirection.set3f(r.direction.x, r.direction.y, r.direction.z);
						progManager.light_spot_shadowmap_lightPosition.set3f(x, y, z);
						progManager.light_spot_shadowmap_screenSize.set2f(w, h);
						progManager.light_spot_shadowmap_radius.set1f(r.spotRadius / 180.0f);
						progManager.light_spot_shadowmap_emission.set1f(r.emission);
						progManager.light_spot_shadowmap_size.set1f(r.pointsize);
						progManager.light_spot_shadowmap_shadowMatrix.setMatrix4f(s.shadowMatrix);
						progManager.light_spot_shadowmap_shadowMapIndex.set1f(s.atlasLocation);
						updateMatrix(progManager.light_spot_shadowmap);
						lightCone.drawAll(GL_TRIANGLES);
						modelMatrix.popMatrix();
					}
				}
			}else {
				// copy from above ============================================
				LightData r = s;
				float x = (float)(r.lightX - renderPosX);
				float y = (float)(r.lightY - renderPosY);
				float z = (float)(r.lightZ - renderPosZ);
				float lightRadius = (float)Math.sqrt(r.emission) * 2.0f;
				if(viewProjFustrum.testSphere(x, y, z, lightRadius)) {
					if(r.type == LightType.POINT) {
						modelMatrix.pushMatrix();
						translateToWorldCoords(r.lightX, r.lightY, r.lightZ);
						modelMatrix.scale(lightRadius);
						progManager.light_point.use();
						progManager.light_point_lightColor.set3f(r.lightR, r.lightG, r.lightB);
						progManager.light_point_lightPosition.set3f(x, y, z);
						progManager.light_point_screenSize.set2f(w, h);
						progManager.light_point_emission.set1f(r.emission);
						progManager.light_point_size.set1f(r.pointsize);
						updateMatrix(progManager.light_point);
						lightSphere.drawAll(GL_TRIANGLES);
						modelMatrix.popMatrix();
					}else if(r.type == LightType.SPOT) {
						modelMatrix.pushMatrix();
						translateToWorldCoords(r.lightX, r.lightY, r.lightZ);
						modelMatrix.rotateTowards(r.direction, up);
						modelMatrix.rotate(-90.0f * MathUtil.toRadians, 1.0f, 0.0f, 0.0f);
						modelMatrix.scale(1.0f, lightRadius, 1.0f);
						float spotRadius2 = (float)Math.sqrt(r.spotRadius * 5.0f);
						modelMatrix.scale(spotRadius2, 1.0f, spotRadius2);
						progManager.light_spot.use();
						progManager.light_spot_lightColor.set3f(r.lightR, r.lightG, r.lightB);
						progManager.light_spot_lightDirection.set3f(r.direction.x, r.direction.y, r.direction.z);
						progManager.light_spot_lightPosition.set3f(x, y, z);
						progManager.light_spot_screenSize.set2f(w, h);
						progManager.light_spot_radius.set1f(r.spotRadius / 180.0f);
						progManager.light_spot_emission.set1f(r.emission);
						progManager.light_spot_size.set1f(r.pointsize);
						updateMatrix(progManager.light_spot);
						lightCone.drawAll(GL_TRIANGLES);
						modelMatrix.popMatrix();
					}
				}
			}
			s.objectsInFrustum = null;
		}
		
		glDisable(GL_CULL_FACE);
		
		// ================================================= RENDER SUN DIFFUSE AND SPECULAR =======================================================
		
		sunShadowBuffer.bindColorTexture(0, 3);
		progManager.light_sun.use();
		updateMatrix(progManager.light_sun);
		
		Vector3f sunDir = scene.sunDirection;
		
		progManager.light_sun_direction.set3f(sunDir.x, sunDir.y, sunDir.z);

		int kelvin = scene.sunKelvin;
		progManager.light_sun_color.set3f(colorTemperatures.getLinearR(kelvin) * scene.sunBrightness * 0.1f, colorTemperatures.getLinearG(kelvin) * scene.sunBrightness * 0.1f, colorTemperatures.getLinearB(kelvin) * scene.sunBrightness * 0.1f);
		
		quadArray.draw(GL_TRIANGLES, 0, 6);
		
		glDisable(GL_BLEND);
		glCullFace(GL_BACK);

		// ================================================= COMBINE G BUFFERS =======================================================
		
		combinedBuffer.setSize(w, h);
		combinedBuffer.bindFramebuffer();
		
		glViewport(0, 0, w, h);
		glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
		glClear(GL_COLOR_BUFFER_BIT);
		
		progManager.gbuffer_combined.use();
		updateMatrix(progManager.gbuffer_combined);
		gBuffer.bindColorTexture(0, 0);
		gBuffer.bindColorTexture(1, 1);
		gBuffer.bindColorTexture(2, 2);
		gBuffer.bindColorTexture(3, 3);
		lightBuffer.bindColorTexture(0, 4);
		lightBuffer.bindColorTexture(1, 5);
		ambientOcclusionBuffer.bindColorTexture(0, 6);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		quadArray.draw(GL_TRIANGLES, 0, 6);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		
		// =================================================== RENDER SKY =======================================================
		
		if(frameCounterTotal % 9 == 0) {
			
			skyBuffer.setSize(w, h);
			skyBuffer.bindFramebuffer();
			
			progManager.sky.use();
			updateMatrix(progManager.sky);
			
			kelvin = scene.sunKelvin;
			float scale = 10.0f;
			progManager.sky_sunColor.set3f(colorTemperatures.getLinearR(kelvin) * scene.sunBrightness * scale, colorTemperatures.getLinearG(kelvin) * scene.sunBrightness * scale, colorTemperatures.getLinearB(kelvin) * scene.sunBrightness * scale);
			
			progManager.sky_sunDirection.set3f(scene.sunDirection.x, scene.sunDirection.y, scene.sunDirection.z);
			progManager.sky_sunSize.set1f(scene.sunSize);
			
			skyDome.drawAll(GL_TRIANGLES);
			
		}

		combinedBuffer.setSize(w, h);
		combinedBuffer.bindFramebuffer();
		
		glEnable(GL_STENCIL_TEST);
		glStencilMask(0x0);
		glStencilFunc(GL_EQUAL, 0, 0xFF);
		glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);

		progManager.p3f2f_texture.use();
		progManager.p3f2f_texture.matrix_mvp.setMatrix4f(multipliedMatrix.identity());
		skyBuffer.bindColorTexture(0);
		quadArray.draw(GL_TRIANGLES, 0, 6);

		glDisable(GL_STENCIL_TEST);
		
		if(GameConfiguration.enableVolumetricLighting && scene.lightShafts) {
			// ================================================= RENDER VIEW SPACE POS MAP =======================================================
			
			postBufferA.setSize(w, h);
			postBufferA.bindFramebuffer();
			
			glViewport(0, 0, w, h);
			glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
			glClear(GL_COLOR_BUFFER_BIT);
			
			progManager.add_positions.use();
			modelMatrix.pushMatrix();
			modelMatrix.scale(1000.0f);
			updateMatrix(progManager.add_positions);
			skyDome.drawAll(GL_TRIANGLES);
			modelMatrix.popMatrix();
			/*
			progManager.position_to_view.use();
			updateMatrix(progManager.position_to_view);
			gBuffer.bindColorTexture(3, 0);
			quadArray.draw(GL_TRIANGLES, 0, 6);
			*/
			// ================================================= RENDER LIGHT SHAFT MAP =======================================================
			ambientOcclusionBuffer.setSize(w / 2, h / 2);
			ambientOcclusionBuffer.bindFramebuffer();
	
			glViewport(0, 0, w / 2, h / 2);
			glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
			glClear(GL_COLOR_BUFFER_BIT);
			glDisable(GL_DEPTH_TEST);
			glDisable(GL_CULL_FACE);
			glDepthMask(false);
			
			progManager.light_shaft_generate.use();
			progManager.light_shaft_generate_shadowMatrixA.setMatrix4f(sunShadowProjViewA);
			progManager.light_shaft_generate_shadowMatrixB.setMatrix4f(sunShadowProjViewB);
			progManager.light_shaft_generate_matrix_v_inv.setMatrix4f(cameraMatrix.invert(multipliedMatrix));
			updateMatrix(progManager.light_shaft_generate);
			postBufferA.bindColorTexture(0, 0);
			sunShadowMap.bindDepthTexture(1);
			gBuffer.bindColorTexture(3, 2);
			quadArray.draw(GL_TRIANGLES, 0, 6);
		}
/*
		// ================================================= BLUR HORIZONTAL LIGHT SHAFT =======================================================
		
		ambientOcclusionBlur.setSize(w / 2, h / 2);
		ambientOcclusionBlur.bindFramebuffer();
		
		progManager.ssao_blur.use();
		progManager.ssao_blur_blurDirection.set2f(4.0f / w, 0.0f);
		updateMatrix(progManager.ssao_blur);
		//linearDepthBuffer.bindColorTexture(0, 1);
		gBuffer.bindDepthTexture(1);
		ambientOcclusionBuffer.bindColorTexture(0, 0);
		quadArray.draw(GL_TRIANGLES, 0, 6);

		// ================================================= BLUR VERTICAL LIGHT SHAFT =======================================================
		
		ambientOcclusionBuffer.setSize(w / 2, h / 2);
		ambientOcclusionBuffer.bindFramebuffer();
		
		progManager.ssao_blur.use();
		progManager.ssao_blur_blurDirection.set2f(0.0f, 4.0f / h);
		updateMatrix(progManager.ssao_blur);
		ambientOcclusionBlur.bindColorTexture(0, 0);
		//linearDepthBuffer.bindColorTexture(0, 1);
		gBuffer.bindDepthTexture(1);
		quadArray.draw(GL_TRIANGLES, 0, 6);
*/
		// ================================================= RENDER FOG OVERLAY =======================================================
		
		combinedBuffer.bindFramebuffer();
		
		glViewport(0, 0, w, h);
		
		//glEnable(GL_STENCIL_TEST);
		//glStencilMask(0x0);
		//glStencilFunc(GL_NOTEQUAL, 0, 0xFF);
		//glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
		glDisable(GL_STENCIL_TEST);
		
		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		
		progManager.blend_atmosphere.use();
		progManager.blend_atmosphere_invTextureSize.set2f(2.0f / ((w / 2) * 2), 2.0f / ((h / 2) * 2));
		
		kelvin = scene.fogKelvin;
		float fogR = colorTemperatures.getLinearR(kelvin);
		float fogG = colorTemperatures.getLinearG(kelvin);
		float fogB = colorTemperatures.getLinearB(kelvin);
		progManager.blend_atmosphere_fogColor.set3f(fogR * scene.skyBrightness, fogG * scene.skyBrightness, fogB * scene.skyBrightness);
		
		progManager.blend_atmosphere_enableLightShafts.set1i((GameConfiguration.enableVolumetricLighting && scene.lightShafts) ? 1 : 0);
		progManager.blend_atmosphere_fogDensity.set1f(scene.fogDensity);
		gBuffer.bindColorTexture(3, 0);
		ambientOcclusionBuffer.bindColorTexture(0, 1);
		//glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		quadArray.draw(GL_TRIANGLES, 0, 6);
		//glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		
		glDisable(GL_BLEND);
		
		// ================================================= DOWNSCALE =======================================================
		
		projMatrix.identity();
		cameraMatrix.identity();
		viewProjMatrix.identity();
		modelMatrix.clear();
		
		postBufferA.setSize(w, h);
		postBufferA.bindFramebuffer();
		
		glViewport(0, 0, w / 2, h / 2);
		
		progManager.p3f2f_texture.use();
		updateMatrix(progManager.p3f2f_texture);
		combinedBuffer.bindColorTexture(0);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		quadArray.draw(GL_TRIANGLES, 0, 6);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		
		// ================================================= DOWNSCALE =======================================================
		
		postBufferB.setSize(w, h);
		postBufferB.bindFramebuffer();
		
		glViewport(0, 0, w / 4, h / 4);
		
		progManager.p3f2f_texture.use();
		modelMatrix.pushMatrix();
		modelMatrix.translate(1.0f, 1.0f, 0.0f);
		modelMatrix.scale(2.0f);
		updateMatrix(progManager.p3f2f_texture);
		postBufferA.bindColorTexture(0);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		quadArray.draw(GL_TRIANGLES, 0, 6);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		modelMatrix.popMatrix();
		
		if(nextTick) {
			// ================================================= DOWNSCALE =======================================================
			
			postBufferC.setSize(w, h);
			postBufferC.bindFramebuffer();
			
			glViewport(0, 0, w / 8, h / 8);
			
			progManager.p3f2f_texture.use();
			modelMatrix.pushMatrix();
			modelMatrix.translate(1.0f, 1.0f, 0.0f);
			modelMatrix.scale(2.0f);
			updateMatrix(progManager.p3f2f_texture);
			postBufferB.bindColorTexture(0);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
			quadArray.draw(GL_TRIANGLES, 0, 6);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
			modelMatrix.popMatrix();
			
			// ========================================= DOWNSCALE TO SINGLE PIXEL ==============================================
			
			exposureCalcTexture.setSize(1, 1);
			exposureCalcTexture.bindFramebuffer();
			
			glViewport(0, 0, 1, 1);
			
			progManager.post_downscale8th.use();
			progManager.post_downscale8th_textureSize.set2f(w, h);
			postBufferC.bindColorTexture(0);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
			quadArray.draw(GL_TRIANGLES, 0, 6);
			nextTick = false;
		}
		
		if(GameConfiguration.enableBloom) {
			// ========================================= HORIZONTAL BLOOM ==============================================
			
			postBufferA.setSize(w, h);
			postBufferA.bindFramebuffer();
			glViewport(0, 0, w / 4, h / 4);
			glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
			glClear(GL_COLOR_BUFFER_BIT);
	
			progManager.post_bloom_h.use();
			progManager.post_bloom_h_screenSizeInv.set2f(0.5f / w, 0.5f / h);
			postBufferB.bindColorTexture(0);
			quadArray.draw(GL_TRIANGLES, 0, 6);
			
			// ========================================= VERTICAL BLOOM ==============================================
			
			postBufferC.setSize(w, h);
			postBufferC.bindFramebuffer();
			glViewport(0, 0, w / 4, h / 4);
			glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
			glClear(GL_COLOR_BUFFER_BIT);
			
			progManager.post_bloom_v.use();
			progManager.post_bloom_v_screenSizeInv.set2f(0.5f / w, 0.5f / h);
			postBufferA.bindColorTexture(0);
			quadArray.draw(GL_TRIANGLES, 0, 6);
		}else {
			postBufferC.setSize(w, h);
			postBufferC.bindFramebuffer();
			glViewport(0, 0, w / 4, h / 4);
			glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
			glClear(GL_COLOR_BUFFER_BIT);
		}
		
		// ================================================= BLOOM COMBINE LENS =======================================================

		postBufferA.setSize(w, h);
		postBufferA.bindFramebuffer();

		glViewport(0, 0, w, h);
		
		progManager.bloom_combine_lens.use();
		progManager.bloom_combine_lens_startRandom.set1f(grainStartRandom);
		progManager.bloom_combine_lens_endRandom.set1f(grainEndRandom);
		progManager.bloom_combine_lens_randomTransition.set1f(client.partialTicks);
		combinedBuffer.bindColorTexture(0);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		postBufferC.bindColorTexture(0, 1);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		quadArray.draw(GL_TRIANGLES, 0, 6);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		combinedBuffer.bindColorTexture(0);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		
		// ================================================= TONEMAP =======================================================

		toneMapped.setSize(w, h);
		toneMapped.bindFramebuffer();

		glViewport(0, 0, w, h);
		
		progManager.post_tonemap.use();
		progManager.post_tonemap_exposure.set1f(exposure);
		postBufferA.bindColorTexture(0);
		quadArray.draw(GL_TRIANGLES, 0, 6);
		
		// ================================================= RENDER FXAA =======================================================
		
		GLStateManager.bindFramebuffer(0);
		glDepthMask(true);

		glViewport(0, 0, w, h);
		glDisable(GL_DEPTH_TEST);
		glDisable(GL_STENCIL_TEST);
		glStencilMask(0x0);
		
		// Edge sharpness: 8.0 (sharp, default) - 2.0 (soft)
	    // Edge threshold: 0.125 (softer, def) - 0.25 (sharper)
	    // 0.06 (faster, dark alias), 0.05 (def), 0.04 (slower, less dark alias)
		
		progManager.post_fxaa.use();
		progManager.post_fxaa_edgeSharpness.set1f(8.0f);
		progManager.post_fxaa_edgeThreshold.set1f(0.125f);
		progManager.post_fxaa_edgeThresholdMin.set1f(0.04f);
		progManager.post_fxaa_screenSize.set2f(w, h);
		
		toneMapped.bindColorTexture(0, 0);
		//ambientOcclusionBuffer.bindColorTexture(0);
		quadArray.draw(GL_TRIANGLES, 0, 6);
		
		++frameCounterTotal;
		++framesPassed;
		if(System.currentTimeMillis() - secondTimer >= 1000l) {
			secondTimer = System.currentTimeMillis();
			prevFramesPassed = framesPassed;
			framesPassed = 0;
			if(client.debugMode) {
				GameClient.log.debug("Framerate: {} ({}ms)", prevFramesPassed, 1000f / prevFramesPassed);
			}
		}
	}
	
	public void updateMatrix(EaglProgram prog) {
		if(prog.matrix_m != null) prog.matrix_m.setMatrix4f(modelMatrix);
		if(prog.matrix_v != null) prog.matrix_v.setMatrix4f(cameraMatrix);
		if(prog.matrix_p != null) prog.matrix_p.setMatrix4f(projMatrix);
		if(prog.matrix_mvp != null) {
			prog.matrix_mvp.setMatrix4f(modelMatrix.mulLocal(viewProjMatrix, multipliedMatrix));
			if(prog.matrix_mvp_inv != null) {
				prog.matrix_mvp.setMatrix4f(multipliedMatrix.invert());
			}
		}else if(prog.matrix_mvp_inv != null) {
			prog.matrix_mvp.setMatrix4f(modelMatrix.mulLocal(viewProjMatrix, multipliedMatrix).invert());
		}
		if(prog.matrix_mv != null) {
			prog.matrix_mv.setMatrix4f(modelMatrix.mulLocal(cameraMatrix, multipliedMatrix));
		}else if(prog.matrix_mv_invtrans != null) {
			prog.matrix_mv_invtrans.setMatrix4f(modelMatrix.mulLocal(cameraMatrix, multipliedMatrix).invert().transpose());
		}
		if(prog.matrix_m_invtrans != null) {
			prog.matrix_m_invtrans.setMatrix4f(modelMatrix.invert(multipliedMatrix).transpose());
		}
		if(prog.matrix_vp_inv != null) {
			prog.matrix_vp_inv.setMatrix4f(viewProjMatrix.invert(multipliedMatrix));
		}
		if(prog.matrix_p_inv != null) {
			prog.matrix_p_inv.setMatrix4f(projMatrix.invert(multipliedMatrix));
		}
	}
	
	public void translateToWorldCoords(double x, double y, double z) {
		modelMatrix.translate(
				(float)(x - renderPosX),
				(float)(y - renderPosY),
				(float)(z - renderPosZ)
		);
	}
	
	public void tick() {
		
		grainEndRandom = grainStartRandom;
		grainStartRandom = rand.nextFloat();
		
		exposureCalcTexture.setSize(1, 1);
		exposureCalcTexture.bindFramebuffer();
		
		glViewport(0, 0, 1, 1);
		
		if(!nextTick) {
			float sceneBrightness = 1.0f;
			
			try(MemoryStack s = MemoryStack.stackPush()) {
				FloatBuffer buf = s.malloc(4).order(ByteOrder.nativeOrder()).asFloatBuffer();
				exposureCalcTexture.bindColorTexture(0);
				glReadPixels(0, 0, 1, 1, GL_RED, GL_FLOAT, buf);
				sceneBrightness = buf.get(0);
			}
			
			nextTick = true;
			
			sceneBrightness += 0.1f;
			sceneBrightness *= 3.0f;
			
			targetExposure = 1.0f / sceneBrightness;
			
			if(targetExposure < 0.3f) targetExposure = 0.3f;
			
			exposure += (targetExposure - exposure) * 0.03f;
		}
	}
	
	private final Vector4f[] bbVertexes = new Vector4f[8];
	
	public boolean testBBFrustum(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, Matrix4f modelMatrix, FrustumIntersection viewProjFustrum2) {

		bbVertexes[0].x = minX;
		bbVertexes[0].y = minY;
		bbVertexes[0].z = minZ;
		bbVertexes[0].w = 1.0f;
		
		bbVertexes[1].x = minX;
		bbVertexes[1].y = minY;
		bbVertexes[1].z = maxZ;
		bbVertexes[1].w = 1.0f;
		
		bbVertexes[2].x = maxX;
		bbVertexes[2].y = minY;
		bbVertexes[2].z = maxZ;
		bbVertexes[2].w = 1.0f;
		
		bbVertexes[3].x = maxX;
		bbVertexes[3].y = minY;
		bbVertexes[3].z = minZ;
		bbVertexes[3].w = 1.0f;
		
		bbVertexes[4].x = minX;
		bbVertexes[4].y = maxY;
		bbVertexes[4].z = minZ;
		bbVertexes[4].w = 1.0f;
		
		bbVertexes[5].x = minX;
		bbVertexes[5].y = maxY;
		bbVertexes[5].z = maxZ;
		bbVertexes[5].w = 1.0f;
		
		bbVertexes[6].x = maxX;
		bbVertexes[6].y = maxY;
		bbVertexes[6].z = maxZ;
		bbVertexes[6].w = 1.0f;
		
		bbVertexes[7].x = maxX;
		bbVertexes[7].y = maxY;
		bbVertexes[7].z = minZ;
		bbVertexes[7].w = 1.0f;
		
		modelMatrix.transform(bbVertexes[0]);
		modelMatrix.transform(bbVertexes[1]);
		modelMatrix.transform(bbVertexes[2]);
		modelMatrix.transform(bbVertexes[3]);
		modelMatrix.transform(bbVertexes[4]);
		modelMatrix.transform(bbVertexes[5]);
		modelMatrix.transform(bbVertexes[6]);
		modelMatrix.transform(bbVertexes[7]);

		float outMinX = 0.0f;
		float outMinY = 0.0f;
		float outMinZ = 0.0f;
		float outMaxX = 0.0f;
		float outMaxY = 0.0f;
		float outMaxZ = 0.0f;

		for(int i = 0; i < 8; ++i) {
			if(bbVertexes[i].x < outMinX || outMinX == 0.0f) outMinX = bbVertexes[i].x;
			if(bbVertexes[i].y < outMinY || outMinY == 0.0f) outMinY = bbVertexes[i].y;
			if(bbVertexes[i].z < outMinZ || outMinZ == 0.0f) outMinZ = bbVertexes[i].z;
			if(bbVertexes[i].x > outMaxX || outMaxX == 0.0f) outMaxX = bbVertexes[i].x;
			if(bbVertexes[i].y > outMaxY || outMaxY == 0.0f) outMaxY = bbVertexes[i].y;
			if(bbVertexes[i].z > outMaxZ || outMaxZ == 0.0f) outMaxZ = bbVertexes[i].z;
		}
		
		//System.out.println("[" + outMinX + ", " + outMinY + ", " + outMinZ + "] [" + outMaxX + ", " + outMaxY + ", " + outMaxZ + "]");
		
		return viewProjFustrum2.testAab(outMinX, outMinY, outMinZ, outMaxX, outMaxY, outMaxZ);
	}
	
	/*
	public float toLocalX(double worldX) {
		return (float)(worldX - (client.prevRenderX + (client.renderX - client.prevRenderX) * client.partialTicks));
	}
	
	public float toLocalY(double worldY) {
		return (float)(worldY - (client.prevRenderY + (client.renderY - client.prevRenderY) * client.partialTicks));
	}
	
	public float toLocalZ(double worldZ) {
		return (float)(worldZ - (client.prevRenderZ + (client.renderZ - client.prevRenderZ) * client.partialTicks));
	}
	*/
	
	public void destory() {
		this.quadArray.destroyWithBuffers();
		this.combinedBuffer.destroy();
		this.gBuffer.destroy();
		this.lightBuffer.destroy();
		this.testGraphic.destroy();
		this.bananaTexture.destroy();
		this.testModelRenderer.array.destroyWithBuffers();
		this.bananaRenderer.array.destroyWithBuffers();
		this.lightCone.destroyWithBuffers();
		this.lightSphere.destroyWithBuffers();
		this.lightHemisphere.destroyWithBuffers();
		this.testModelTexture.destroy();
		this.sunShadowMap.destroy();
		this.sunShadowBuffer.destroy();
		this.lightShadowMap.destroy();
		this.ambientOcclusionBuffer.destroy();
		this.ambientOcclusionBlur.destroy();
		this.linearDepthBuffer.destroy();
		this.postBufferA.destroy();
		this.postBufferB.destroy();
		this.postBufferC.destroy();
		this.toneMapped.destroy();
		this.exposureCalcTexture.destroy();
		this.skyDome.destroy();
		this.skyBuffer.destroy();
	}

}
