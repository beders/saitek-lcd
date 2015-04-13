# saitek-lcd
Simple Java API to access the LCD screen of a Saitek X52 Pro.

This API enables adding pages of text to the LCD screen as well as control theLEDs.

Pages can be scrolled with the up and down soft buttons.
Pages can be switched with the Pg Up and Down buttons.
Pages can be associated with a button LED configuration (i.e. turn LEDs off/on, turn them red/green/amber).

## Note
Initial release. Has only seen testing on a 64-bit Windows 7 with the latest X52 Pro drivers.
Let me know if you run into troubles.

## Usage

Create an instance of Saitek with paths to the DirectOutput.DLL.
See example for standard paths.

See documentation of us.monoid.saitek.Saitek for more information.

## Example
This adds two pages to the display and changes some LEDs for the second page.
Run it with `mvn package` then `java -jar SaitekLCD-x.y-SNAPSHOT.jar`

```
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
```

## Build

```
mvn package
```



