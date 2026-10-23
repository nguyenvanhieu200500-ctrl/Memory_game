package memorygame.server;

import java.awt.*;
import java.awt.geom.Path2D;
import javax.swing.*;

/** Decorative artwork; never contains server or player data. */
final class MonitorArtwork {
    private MonitorArtwork() {}
    static void background(Graphics2D g, int w, int h) {
        g.setPaint(new LinearGradientPaint(0, 0, 0, Math.max(1, h),
                new float[]{0f, .12f, .28f, .87f, 1f},
                new Color[]{new Color(0x8275F5), new Color(0xB4A2F3), new Color(0xFCF3E9),
                        new Color(0xFFF7EC), new Color(0xD7B5FC)}));
        g.fillRect(0, 0, w, h);
        g.setPaint(new RadialGradientPaint(w * .56f, 0, Math.max(1, w * .4f),
                new float[]{0f, 1f}, new Color[]{new Color(234, 220, 255, 145), new Color(234, 220, 255, 0)}));
        g.fillRect(0, 0, w, h / 3);
        for (int i = 0; i < 10; i++) {
            int x = i * w / 9 - 60;
            g.setColor(new Color(245, 220, 255, 65));
            g.fillOval(x, h / 8 + (i % 3) * 9, 140, 65);
            g.setColor(new Color(190, 155, 255, 60));
            g.fillOval(x, h - 45 - (i % 3) * 10, 130, 90);
        }
        for (int i = 0; i < 20; i++) star(g, (i * 173 + 25) % Math.max(1, w),
                (i * 97 + 14) % Math.max(1, h), 4, new Color(255, 255, 255, 145));
    }
    static JComponent illustration(int width, int height) {
        return new JComponent() {
            { setPreferredSize(new Dimension(width, height)); }
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                cards(g, getWidth() / 2, getHeight() / 2, Math.min(getWidth() / 330.0, getHeight() / 190.0));
                g.dispose();
            }
        };
    }
    static void cards(Graphics2D parent, int x, int y, double scale) {
        Graphics2D g = (Graphics2D) parent.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(x, y); g.scale(scale, scale);
        g.setPaint(new RadialGradientPaint(0, 0, 170, new float[]{0, 1},
                new Color[]{new Color(222, 170, 255, 110), new Color(255, 255, 255, 0)}));
        g.fillOval(-170, -95, 340, 190);
        g.setColor(new Color(144, 87, 221, 35)); g.fillOval(-88, 78, 176, 15);
        card(g, -47, -5, -.27, new Color(0x9840E9), false);
        card(g, 49, 7, .25, new Color(0x20B6DB), true);
        g.setStroke(new BasicStroke(4f)); g.setColor(new Color(249, 172, 228, 160));
        g.drawArc(-145, -20, 290, 78, 170, 150);
        star(g, -125, -45, 10, new Color(0xB57BEF)); star(g, 120, 45, 10, new Color(0xFFCC57));
        star(g, 100, -76, 13, new Color(0xFFCC57)); star(g, -140, 30, 10, new Color(0xFFCC57));
        g.dispose();
    }
    private static void card(Graphics2D parent, int x, int y, double angle, Color edge, boolean bolt) {
        Graphics2D g = (Graphics2D) parent.create();g.translate(x,y);g.rotate(angle);
        g.setColor(new Color(126,65,184,30));g.fillRoundRect(-44,-62,94,134,14,14);
        g.setPaint(new GradientPaint(0,-65,Color.WHITE,0,65,new Color(0xF5F7FF)));
        g.fillRoundRect(-46,-65,92,130,14,14);g.setColor(edge);g.setStroke(new BasicStroke(3));g.drawRoundRect(-46,-65,92,130,14,14);
        Path2D shape=new Path2D.Double();
        if(bolt){shape.moveTo(8,-30);shape.lineTo(-24,5);shape.lineTo(-3,6);shape.lineTo(-9,32);shape.lineTo(25,-6);shape.lineTo(5,-7);}
        else for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5,r=i%2==0?28:14;if(i==0)shape.moveTo(Math.cos(a)*r,Math.sin(a)*r);else shape.lineTo(Math.cos(a)*r,Math.sin(a)*r);}
        shape.closePath();g.setColor(bolt?new Color(0x09AFC4):new Color(0xFFBF24));g.fill(shape);g.dispose();
    }
    private static void star(Graphics2D g,int x,int y,int r,Color color){
        g.setColor(color);g.fillPolygon(new int[]{x,x+r/3,x+r,x+r/3,x,x-r/3,x-r,x-r/3},
                new int[]{y-r,y-r/3,y,y+r/3,y+r,y+r/3,y,y-r/3},8);
    }
}
