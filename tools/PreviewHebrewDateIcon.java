import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Compare day-only and tiny-month prototypes at Android's 24px status-icon size.
 * java -Djava.awt.headless=true tools/PreviewHebrewDateIcon.java /tmp/hebrew-date-icon-prototype.png
 */
class PreviewHebrewDateIcon {
    static final Font FONT;
    static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    static {
        try { FONT=Font.createFont(Font.TRUETYPE_FONT, Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf").toFile()).deriveFont(24f); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    static void word(Graphics2D g,String text,double top,double height) {
        char[] chars=text.toCharArray();
        Shape outline=FONT.layoutGlyphVector(FRC,chars,0,chars.length,Font.LAYOUT_RIGHT_TO_LEFT).getOutline();
        Rectangle2D b=outline.getBounds2D();
        double scale=Math.min(22/b.getWidth(),height/b.getHeight());
        AffineTransform t=new AffineTransform();t.translate((24-b.getWidth()*scale)/2,top);t.scale(scale,scale);t.translate(-b.getX(),-b.getY());g.fill(t.createTransformedShape(outline));
    }
    public static void main(String[] args)throws Exception {
        BufferedImage sheet=new BufferedImage(408,128,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=sheet.createGraphics();g.setColor(new Color(16,28,39));g.fillRect(0,0,408,128);
        String[] months={"חשון","אדר ב׳","מרחשון","תשרי","ניסן","אלול"};
        for(int i=0;i<months.length;i++) {
            for(int prototype=0;prototype<2;prototype++) {
                BufferedImage icon=new BufferedImage(24,24,BufferedImage.TYPE_INT_ARGB);
                Graphics2D p=icon.createGraphics();p.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);p.setColor(Color.WHITE);
                word(p,"כד",prototype==0?3:1,prototype==0?18:14);
                if(prototype==1)word(p,months[i],18,5);
                p.dispose();
                g.drawImage(icon,i*68+20,prototype*64+8,null);
                g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,9));g.setColor(Color.WHITE);g.drawString(prototype==0?"day":"day + month",i*68+5,prototype*64+47);
            }
        }
        g.dispose();ImageIO.write(sheet,"png",Path.of(args.length==0?"/tmp/hebrew-date-icon-prototype.png":args[0]).toFile());
    }
}
