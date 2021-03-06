package net.lax1dude.cs50_final_project.client.renderer;

import java.util.LinkedHashSet;

import org.joml.Vector3f;

public class RenderScene {
	
	public final LinkedHashSet<ObjectRenderer> objectRenderers = new LinkedHashSet();
	public final LinkedHashSet<LightData> lightRenderers = new LinkedHashSet();
	public final LinkedHashSet<ShadowLightRenderer> shadowLightRenderers = new LinkedHashSet();
	
	//package cache variables
	Vector3f sunDirection = new Vector3f();
	Vector3f moonDirection = new Vector3f();

	int sunKelvin = 3500;
	int fogKelvin = 6000;
	int moonKelvin = 7000;
	float moonBrightness = 2.0f;
	
	boolean moonIsUp = false;

	float sunBrightness = 100.0f;
	float skyBrightness = 1.0f;
	
	public float sunBrightnessFac = 1.0f;
	public float skyBrightnessFac = 1.0f;
	public float sunSize = 0.15f;
	public float fogDensity = 0.005f;
	public boolean lightShafts = true;
	public boolean enableSun = true;
	public float cloudDensity = 0.1f;

	public float windX = 0.01f;
	public float windZ = 0.004f;
	
	public int time = 0;
	int cubemapSunKelvin;
	float cubemapSunBrightness;
	
	public RenderScene() {
		
	}

}
