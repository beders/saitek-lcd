
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package us.monoid.saitek;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import java.io.File;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple API to access the LCD features of a Saitek X52 Pro
 *
 * The native library needed is called DirectOutput.dll and by default is installed in
 * <pre>C:\Program Files\Saitek\DirectOutput</pre>
 * <pre>C:\Program Files (x86)\Saitek\DirectOutput</pre> The SDK documentation is here
 * <pre>file:///C:/Program%20Files/Saitek/DirectOutput/SDK/DirectOutput.htm</pre> Thanks to the authors and contributors of JNA!
 *
 * @author beders@yahoo.com
 */
public class Saitek {

  static final String X52_PRO = "{06D5DA29-3BF9-204F-85FA-1E02C04FAC17}"; // too lazy to re-order bytes, this fake UUID will do
  List<Pointer> devices = new ArrayList<>();
  List<Page> pages = new ArrayList<>();
  Pointer x52pro;
  DirectOutputLibrary dol;
  volatile int activePage;
  private final ExecutorService executor;

  public Saitek(File... libraryPath) {
    // try paths individually
    for (File f : libraryPath) {
      String path = f.toString();
      if (path.length() > 0) {
        // System.out.println("Using jna.library.path " + path);
        System.setProperty("jna.library.path", path);
      }
      try {
        NativeLibrary.getInstance(DirectOutputLibrary.JNA_LIBRARY_NAME);
        dol = (DirectOutputLibrary) Native.loadLibrary(DirectOutputLibrary.JNA_LIBRARY_NAME, DirectOutputLibrary.class);
      } catch (UnsatisfiedLinkError err) {
        System.err.println("Unable to load DirectOutput.dll from " + f + " (running on " + System.getProperty("os.name") + "-" + System.getProperty("os.version") 
                + "-" + System.getProperty("os.arch") + "\nContinuuing...");
        continue;
      }
      System.out.println("Loaded library from " + f);
      break;
    }
    if (dol == null) {
      throw new IllegalArgumentException("Unable to load DirectOutput.dll from the paths provided");
    }
    executor = Executors.newSingleThreadExecutor();
    executor.submit(this::init);
  }

  void init() {
    if (findDevices()) {
      registerCallbacks();
    } else {
      System.out.println("No X52 Pro found! Plug it in and try again");
      executor.shutdown();
    }
  }

  /**
   * Sample usage.
   * Adds two pages, scrolls pages with soft buttons, ends after ~30 seconds
   *
   * @param args the command line arguments
   */
  public static void main(String[] args) throws InterruptedException {
    Saitek s = new Saitek(new File("C:\\Program Files (x86)\\Saitek\\DirectOutput"), new File("C:\\Program Files\\Saitek\\DirectOutput"));

    Page p = s.addPage("First");
    p.addLine("How are you doing?");
    p.addLine("This is fun");
    p.addLine("world.");
    p.addLine("the real");
    p.addLine("to");
    p.addLine("Welcome!");
    p = s.addPage("Second", 20);
    p.addLine("Second page");

    for (int i = 0; i < 5; i++) {
      p.addLine("line " + i);
      Thread.sleep((long) (100 + Math.random() * 300));
    }

    p.amber(X52.FireA).red(X52.Toggle1_2).amber(X52.Toggle3_4).green(X52.FireD).off(X52.Clutch).on(X52.Throttle).off(X52.Toggle5_6);
    
    Thread.sleep(30000);
    s.closeDevice();
  }

  boolean findDevices() {
    WString pluginName = new WString("Saitek");
    dol.DirectOutput_Initialize(pluginName);
    DirectOutputLibrary.HRESULT result = dol.DirectOutput_Enumerate((hDevice, pCtc) -> {
      devices.add(hDevice);
    }, null);

    for (Pointer p : devices) {
      Memory g = new Memory(16);

      DirectOutputLibrary.LPGUID guid = new DirectOutputLibrary.LPGUID(g.getPointer(0));
      result = dol.DirectOutput_GetDeviceType(p, guid);
      String pseudoGuid = toGuidString(guid.getPointer().getByteArray(0, 16)); // the byte order isn't correct, but we don't care right now
      if (X52_PRO.equals(pseudoGuid)) {
        System.out.println("Found X52 Pro! Yay!");
        x52pro = p;
        return true;
      }
    }
    return false;
  }

  private void registerCallbacks() {
    pageCallback = (Pointer hDevice, int dwPage, byte bSetActive, Pointer pCtxt) -> {
      if (bSetActive > 0) {
        executor.submit(() -> {
          activePage = dwPage;
          // System.out.println("Page changed to:" + dwPage);
          pages.get(dwPage).update();
        });
      }
    };
    dol.DirectOutput_RegisterPageCallback(x52pro, pageCallback, null);
    softButtonCallback = (Pointer hDevice, int dwButtons, Pointer pCtxt) -> {
      executor.submit(() -> {
        if ((dwButtons & DirectOutputLibrary.SoftButton_Up) == DirectOutputLibrary.SoftButton_Up) {
          Page p = pages.get(activePage);
          p.scrollUp();
        } else if ((dwButtons & DirectOutputLibrary.SoftButton_Down) == DirectOutputLibrary.SoftButton_Down) {
          Page p = pages.get(activePage);
          p.scrollDown();
        }
      });
    };

    dol.DirectOutput_RegisterSoftButtonCallback(x52pro, softButtonCallback, null);
  }
  // Necessary to hold onto, otherwise the GC will collect these callbacks!
  private DirectOutputLibrary.Pfn_DirectOutput_SoftButtonChange softButtonCallback;
  private DirectOutputLibrary.Pfn_DirectOutput_PageChange pageCallback;

  public Page addPage(String title) {
    return this.addPage(title, 0);
  }

  public Page addPage(String title, int maxCount) {
    final Page page = new Page(title, maxCount);
    try {
      int no = pages.size();
      return executor.submit(() -> {
        WString pageTitle = new WString(title);
        DirectOutputLibrary.HRESULT result = dol.DirectOutput_AddPage(x52pro, no, pageTitle, DirectOutputLibrary.FLAG_SET_AS_ACTIVE);
        pages.add(page);
        activePage = no;
        return page;
      }).get();
    } catch (InterruptedException ex) {
      Logger.getLogger(Saitek.class.getName()).log(Level.SEVERE, null, ex);
    } catch (ExecutionException ex) {
      Logger.getLogger(Saitek.class.getName()).log(Level.SEVERE, null, ex);
    }
    return page;
  }

  private String toGuidString(byte[] bGuid) {
    final String HEXES = "0123456789ABCDEF";

    final StringBuilder hexStr = new StringBuilder(2 * bGuid.length);
    hexStr.append("{");

    for (int i = 0; i < bGuid.length; i++) {
      char ch1 = HEXES.charAt((bGuid[i] & 0xF0) >> 4);
      char ch2 = HEXES.charAt(bGuid[i] & 0x0F);
      hexStr.append(ch1).append(ch2);

      if ((i == 3) || (i == 5) || (i == 7) || (i == 9)) {
        hexStr.append("-");
      }
    }

    hexStr.append("}");
    return hexStr.toString();
  }

  private void closeDevice() {
    executor.submit(() -> {
      // de-register callbacks, pages
      for (int i = 0; i < pages.size(); i++) {
        dol.DirectOutput_RemovePage(x52pro, i);
      }
      dol.DirectOutput_RegisterPageCallback(x52pro, null, null);
      dol.DirectOutput_RegisterSoftButtonCallback(x52pro, null, null);
      dol.DirectOutput_Deinitialize();
    });
    executor.shutdown();
  }

  /**
   * Create a page of lines. Newest lines are shown first in display. If maxCount is > 0, the oldest entry will be removed if list has more
   * than maxCount elements. (Exercise for the reader to replace lines with a linked list.)
   */
  public class Page {

    List<String> lines = Collections.synchronizedList(new ArrayList<>());
    int topLine = 0;
    private final String title;
    int maxCount;
    BitSet ledConfig = new BitSet(20);
    BitSet ledsSet = new BitSet(20);
    
            
    public Page(String title) {
      this(title, 0);
    }

    public Page(String title, int maxCount) {
      this.title = title;
      this.maxCount = maxCount;
    }

    public Page addLine(String line) {
      if (maxCount > 0 && lines.size() == maxCount) {
        lines.remove(lines.size() - 1);
      }
      lines.add(0, line); // yes, array list will have to copy things, but at 30 items or so, we really don't care
      updatePage();
      return this;
    }

    void updatePage() {
      executor.submit(() -> {
        int no = pages.indexOf(this);
        if (no == activePage) {
          for (int i = topLine, row = 0; row < 3; i++, row++) {
            WString line = new WString(i < lines.size() ? lines.get(i) : "");
            DirectOutputLibrary.HRESULT result = dol.DirectOutput_SetString(x52pro, no, row, line.length(), line);
            //System.out.println("Result:" + result);
          }
        }
      });
    }

    public synchronized void scrollUp() {
      topLine = Math.max(topLine - 1, 0);
      // System.out.println("ScrollUp");
      updatePage();      
    }

    public synchronized void scrollDown() {
      topLine = Math.min(lines.size() - 1, topLine + 1);
      // System.out.println("ScrollDown");
      updatePage();
    }

    public synchronized Page green(X52 button) {
      if (!button.isToggle()) {
        setLED(button.greenID,1);
        setLED(button.redID,0);
      }
      return this;
    }
    
    public synchronized Page red(X52 button) {
      if (!button.isToggle()) {
         setLED(button.greenID,0);
        setLED(button.redID,1);
      }
      return this;
    }
    
    public synchronized Page amber(X52 button) {
      if (!button.isToggle()) {
        setLED(button.redID,1);
        setLED(button.greenID,1);
      }
      return this;
    }
    
    public synchronized Page off(X52 button) {
      if (button.isToggle()) {
        setLED(button.id(), 0);
      } else {
        setLED(button.redID,0);
        setLED(button.greenID,0);
      }
      return this;
    }
    
    public synchronized Page on(X52 button) {
      if (button.isToggle()) {
        setLED(button.id(),1);
      }
      return this;
    }
    
    public void update() {
      updatePage();
      updateLEDs();
    }
    
    void updateLEDs() {
      int no = pages.indexOf(this);
      if (!ledsSet.isEmpty() && no == activePage) {
        executor.submit(() -> {
          ledsSet.stream().forEach(ledID -> {        
            dol.DirectOutput_SetLed(x52pro, no, ledID, ledConfig.get(ledID) ? 1 : 0);
          });
        });
      }
    }

    /** Set an LED on (1) or off (0). See the SDK documentation for the specific values or use the X52 enum */
    public synchronized void setLED(int ledID, int i) {
      ledsSet.set(ledID);
      ledConfig.set(ledID, i == 1);
      updateLEDs();
    }
    
  }
  
  public enum X52 {
    Fire(0),
    FireA(1, 2),
    FireB(3, 4),
    FireD(5, 6),
    FireE(7, 8),
    Toggle1_2(9, 10),
    Toggle3_4(11, 12),
    Toggle5_6(13, 14),
    POV2(15, 16),
    Clutch(17, 18),
    Throttle(19);
    
    final int redID, greenID;
    
    X52(int red, int green) {
      this.greenID = green;
      this.redID = red;
    }
    
    X52(int id) {
      this.greenID = -1;
      this.redID = id;
    }
    
    boolean isToggle() {
      return greenID == -1;
    }

    private int id() {
      return redID;
    }
  }
}
