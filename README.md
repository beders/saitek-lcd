# saitek-lcd
Simple Java API to access the LCD screen of a [Saitek X52 Pro](http://www.saitek.com/uk/prod-bak/x52pro.html).

This API enables adding pages of text to the LCD screen as well as control LEDs.

Pages can be scrolled with the up and down soft buttons.

Pages can be switched with the Pg Up and Down buttons.

Pages can be associated with a button LED configuration (i.e. turn LEDs off/on, turn them red/green/amber).

## Note
Initial release. Has only seen testing on a 64-bit Windows 7 with the latest X52 Pro drivers.
Let me know if you run into trouble.

## Usage

Create an instance of the Saitek class with one or more paths to the `DirectOutput.DLL` which is part of the Saitek drivers.
See example for standard paths.

See javadocs of us.monoid.saitek.Saitek for more information.

## Example

The code below adds two pages to the LCD display of the controller and changes some LEDs for the second page.
Run it with `mvn package` then `java -jar SaitekLCD-x.y-SNAPSHOT.jar`

```
import static us.monoid.saitek.Saitek.*;
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



