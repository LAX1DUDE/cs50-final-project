package net.eagtek.metaballs;

import org.lwjgl.glfw.GLFWCharCallbackI;
import org.lwjgl.glfw.GLFWCursorPosCallbackI;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;
import org.lwjgl.glfw.GLFWWindowFocusCallbackI;
import org.lwjgl.glfw.GLFWImage.Buffer;
import org.lwjgl.opengles.GLES;
import org.lwjgl.opengles.GLESCapabilities;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.trove.list.linked.TCharLinkedList;

import static org.lwjgl.egl.EGL10.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWNativeEGL.*;
import static org.lwjgl.opengles.GLES30.*;
import static org.lwjgl.system.MemoryUtil.*;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;

import org.lwjgl.egl.EGL;


public class ANGLEContext {
	
	public static final Logger log = LoggerFactory.getLogger("ANGLEContext");

	public static enum ToolkitPlatform {
		desktop;
	}
	
	public static enum ContextPlatform {
		angle(GLFW_ANGLE_PLATFORM_TYPE_NONE, "default"),
		d3d11(GLFW_ANGLE_PLATFORM_TYPE_D3D11, "d3d11"),
		opengl(GLFW_ANGLE_PLATFORM_TYPE_OPENGL, "opengl"),
		opengles(GLFW_ANGLE_PLATFORM_TYPE_OPENGLES, "opengles"),
		metal(GLFW_ANGLE_PLATFORM_TYPE_METAL, "metal"),
		vulkan(GLFW_ANGLE_PLATFORM_TYPE_VULKAN, "vulkan");
		
		protected final int eglEnum;
		protected final String id;
		
		private ContextPlatform(int eglEnum, String id) {
			this.eglEnum = eglEnum;
			this.id = id;
		}
		
	}

	public final ToolkitPlatform toolkit;
	public final ContextPlatform platform;
	private String title;

	private long glfw_windowHandle = 0l;
	private long glfw_eglHandle = 0l;
	
	private GLESCapabilities caps = null;
	
	public GLESCapabilities getGLESCapabilities() {
		return caps;
	}
	
	public ANGLEContext(ToolkitPlatform toolkit, ContextPlatform platform, String title) {
		this.toolkit = toolkit;
		this.platform = platform;
		this.title = title;
	}

	public void create() {
		if(toolkit == ToolkitPlatform.desktop) {
			createGLFW();
		}
	}

	private void createGLFW() {
		GLFWErrorCallback.createThrow().set();
		
		glfwInit();
		
		log.info("loading GLFW {}", glfwGetVersionString());
		
		glfwDefaultWindowHints();
		glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE);
		glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        
        glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_EGL_CONTEXT_API);
        glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_ES_API);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 0);
        
        //glfwWindowHint(GLFW_ANGLE_PLATFORM_TYPE, platform.eglEnum);
        
        glfw_windowHandle = glfwCreateWindow(854, 480, title, NULL, NULL);
        glfw_eglHandle = glfwGetEGLDisplay();
        
        int[] major = new int[] { 1 };
        int[] minor = new int[] { 4 };
        if(!eglInitialize(glfw_eglHandle, major, minor)) {
        	throw new RuntimeException("Could not initialize EGL");
        }
        
        EGL.createDisplayCapabilities(glfw_eglHandle, major[0], minor[0]);
        glfwMakeContextCurrent(glfw_windowHandle);
        
        caps = GLES.createCapabilities();

        log.info("OpenGL Version: {}", glGetString(GL_VERSION));
        log.info("OpenGL Vendor: {}", glGetString(GL_VENDOR));
        log.info("OpenGL Renderer: {}", glGetString(GL_RENDERER));

        glfwSetKeyCallback(glfw_windowHandle, new GLFWKeyCallbackI() {
			@Override
			public void invoke(long window, int key, int scancode, int action, int mods) {
				if(keyEvents.size() < 256) {
					keyEvents.add(new KeyboardEvent(key, (action == GLFW_PRESS) || (action == GLFW_REPEAT), action == GLFW_REPEAT));
				}
			}
		});
        
        glfwSetCharCallback(glfw_windowHandle, new GLFWCharCallbackI() {
			@Override
			public void invoke(long window, int codepoint) {
				if(keyChars.size() < 256) {
					keyChars.addAll(Character.toChars(codepoint));
				}
			}
		});
        
        glfwSetCursorPosCallback(glfw_windowHandle, new GLFWCursorPosCallbackI() {
			@Override
			public void invoke(long window, double xpos, double ypos) {
				ANGLEContext.this.mousex = (int)xpos;
				ANGLEContext.this.mousey = (int)ypos;
			}
		});
        
        glfwSetMouseButtonCallback(glfw_windowHandle, new GLFWMouseButtonCallbackI() {
			@Override
			public void invoke(long window, int button, int action, int mods) {
				if(mouseEvents.size() < 256) {
					mouseEvents.add(new MouseEvent(action == GLFW_PRESS, button, ANGLEContext.this.mousex, ANGLEContext.this.mousey, false, 0.0f, 0.0f));
				}
			}
		});
        
        glfwSetWindowFocusCallback(glfw_windowHandle, new GLFWWindowFocusCallbackI() {
			@Override
			public void invoke(long window, boolean focused) {
				ANGLEContext.this.focused = focused;
			}
		});
        
	}
	
	public void destroy() {
		if(toolkit == ToolkitPlatform.desktop) {
			destroyGLFW();
		}
	}

	private void destroyGLFW() {
		GLES.setCapabilities(null);
		glfwTerminate();
	}
	
	public void setTitle(String s) {
		if(toolkit == ToolkitPlatform.desktop) {
			glfwSetWindowTitle(glfw_windowHandle, title = s);
		}
	}
	
	public void setSize(int w, int h) {
		if(toolkit == ToolkitPlatform.desktop) {
			glfwSetWindowSize(glfw_windowHandle, w, h);
		}
	}
	
	public int getOuterWidth() {
		if(toolkit == ToolkitPlatform.desktop) {
			int[] w = new int[1];
			int[] h = new int[1];
			glfwGetWindowSize(glfw_windowHandle, w, h);
			return w[0];
		}
		return 0;
	}
	
	public int getOuterHeight() {
		if(toolkit == ToolkitPlatform.desktop) {
			int[] w = new int[1];
			int[] h = new int[1];
			glfwGetWindowSize(glfw_windowHandle, w, h);
			return h[0];
		}
		return 0;
	}

	public int getInnerWidth() {
		if(toolkit == ToolkitPlatform.desktop) {
			int[] w = new int[1];
			int[] h = new int[1];
			glfwGetFramebufferSize(glfw_windowHandle, w, h);
			return w[0];
		}
		return 0;
	}
	
	public int getInnerHeight() {
		if(toolkit == ToolkitPlatform.desktop) {
			int[] w = new int[1];
			int[] h = new int[1];
			glfwGetFramebufferSize(glfw_windowHandle, w, h);
			return h[0];
		}
		return 0;
	}
	
	public String getClipboardString() {
		if(toolkit == ToolkitPlatform.desktop) {
			return glfwGetClipboardString(glfw_windowHandle);
		}
		return null;
	}
	
	public void setClipboardString(String s) {
		if(toolkit == ToolkitPlatform.desktop) {
			glfwSetClipboardString(glfw_windowHandle, s);
		}
	}
	
	public void setFullscreen(boolean fs) {
		throw new RuntimeException("not implemented");
	}
	
	public boolean getFullscreen() {
		throw new RuntimeException("not implemented");
	}
	
	public void setIcons(BufferedImage[] icons) {
		try {
			try (MemoryStack memorystack = MemoryStack.stackPush()) {
				Buffer buffer = GLFWImage.mallocStack(icons.length, memorystack);
				for(int i = 0; i < icons.length; ++i) {
					buffer.position(i);
					buffer.width(icons[i].getWidth());
					buffer.height(icons[i].getHeight());
					
					int[] image = icons[i].getRGB(0, 0, icons[i].getWidth(), icons[i].getHeight(), null, 0, icons[i].getWidth());
					
					for(int j = 0; j < image.length; ++j) {
						image[j] = image[j] << 8;
					}
					
					ByteBuffer imageBuffer = memorystack.malloc(image.length * 4).order(ByteOrder.BIG_ENDIAN);
					
					imageBuffer.asIntBuffer().put(image);
					
					buffer.pixels(imageBuffer);
				}
				glfwSetWindowIcon(glfw_windowHandle, buffer);
			}
		}catch(Throwable t) {
		}
	}
	
	public void swapBuffers(boolean sync) {
		if(toolkit == ToolkitPlatform.desktop) {
			glfwSwapInterval(sync ? 1 : 0);
			glfwSwapBuffers(glfw_windowHandle);
		}
	}
	
	public void pollEvents() {
		if(toolkit == ToolkitPlatform.desktop) {
			glfwPollEvents();
		}
	}
	
	public boolean closeRequested() {
		if(toolkit == ToolkitPlatform.desktop) {
			return glfwWindowShouldClose(glfw_windowHandle);
		}
		return false;
	}
	
	private boolean keyRepeat = true;
	
	public boolean getKeyRepeatEnabled() {
		return keyRepeat;
	}
	
	public void setKeyRepeatEnabled(boolean e) {
		keyRepeat = e;
	}

	private final TCharLinkedList keyChars = new TCharLinkedList();
	
	public char nextKeyboardChar() {
		if(keyChars.size() > 0) {
			char c = keyChars.get(0);
			keyChars.remove(0, 1);
			return c;
		}else {
			return '\0';
		}
	}
	
	public static class KeyboardEvent {
		
		public final int code;
		public final boolean pressed;
		public final boolean repeated;
		
		protected KeyboardEvent(int code, boolean pressed, boolean repeated) {
			this.code = code;
			this.pressed = pressed;
			this.repeated = repeated;
		}
		
	}
	
	private final LinkedList<KeyboardEvent> keyEvents = new LinkedList();

	public KeyboardEvent keyboardNext() {
		if(keyEvents.size() > 0) {
			return keyEvents.remove(0);
		}else {
			return null;
		}
	}
	
	public boolean isKeyDown(int key) {
		if(toolkit == ToolkitPlatform.desktop) {
			return glfwGetKey(glfw_windowHandle, key) == GLFW_PRESS;
		}
		return false;
	}
	
	public String getKeyName(int key) {
		if(toolkit == ToolkitPlatform.desktop) {
			return glfwGetKeyName(key, 0);
		}
		return null;
	}
	
	public static class MouseEvent {

		public final boolean pressed;
		public final int button;
		public final int x;
		public final int y;
		
		public final boolean dwheel;
		public final float dwheelx;
		public final float dwheely;
		
		protected MouseEvent(boolean pressed, int button, int x, int y, boolean dwheel, float dwheelx, float dwheely) {
			this.pressed = pressed;
			this.button = button;
			this.x = x;
			this.y = y;
			this.dwheel = dwheel;
			this.dwheelx = dwheelx;
			this.dwheely = dwheely;
		}
	}

	private final LinkedList<MouseEvent> mouseEvents = new LinkedList();
	
	public MouseEvent mouseNext() {
		if(mouseEvents.size() > 0) {
			return mouseEvents.remove(0);
		}else {
			return null;
		}
	}
	
	public boolean isMouseDown(int button) {
		if(toolkit == ToolkitPlatform.desktop) {
			return glfwGetMouseButton(glfw_windowHandle, button) == GLFW_PRESS;
		}
		return false;
	}

	public void setMouseGrabbed(boolean grabbed) {
		if(toolkit == ToolkitPlatform.desktop) {
			glfwSetInputMode(glfw_windowHandle, GLFW_CURSOR, grabbed ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
		}
	}
	
	private boolean focused = true;
	
	public boolean windowHasFocus() {
		return focused;
	}
	
	public boolean getMouseGrabbed() {
		if(toolkit == ToolkitPlatform.desktop) {
			return glfwGetInputMode(glfw_windowHandle, GLFW_CURSOR) == GLFW_CURSOR_DISABLED;
		}
		return false;
	}
	
	private int mousex = 0;
	private int mousey = 0;
	
	public int mouseX() {
		return mousex;
	}
	
	public int mouseY() {
		return mousey;
	}
	
	public void setMousePos(int x, int y) {
		if(toolkit == ToolkitPlatform.desktop) {
			glfwSetCursorPos(glfw_windowHandle, x, y);
		}
	}
	
	/*

	public static class ControllerEvent {
		
		public final boolean pressed;
		public final int button;
		
		protected ControllerEvent(boolean pressed, int button) {
			this.pressed = pressed;
			this.button = button;
		}
		
	}
	
	private final LinkedList<ControllerEvent> controllerEvents = new LinkedList();

	public ControllerEvent controllerNext() {
		if(controllerEvents.size() > 0) {
			return controllerEvents.remove(0);
		}else {
			return null;
		}
	}
	
	public boolean getControllerButtonDown(int button) {
		return false;
	}
	
	public float getControllerMainX() {
		return 0f;
	}
	
	public float getControllerMainY() {
		return 0f;
	}
	
	public float getControllerPovX() {
		return 0f;
	}
	
	public float getControllerPovY() {
		return 0f;
	}
	*/

}
