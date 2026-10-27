package memorygame.client;

import memorygame.common.AppTheme;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.*;

/** Shop-only decoration; purchase and equipment state remain server-owned. */
final class ShopStyle {
    private ShopStyle() {}

    static Graphics2D graphics(Graphics source) {
        Graphics2D g = (Graphics2D) source.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    static class Surface extends JPanel {
        private final boolean tinted;
        Surface(LayoutManager layout, boolean tinted) {
            super(layout); this.tinted = tinted; setOpaque(false);
        }
        @Override protected void paintComponent(Graphics source) {
            Graphics2D g = graphics(source);
            int w = getWidth(), h = getHeight();
            g.setColor(new Color(69, 35, 110, 10));
            g.fillRoundRect(2, 5, w - 4, h - 6, 24, 24);
            g.setPaint(new GradientPaint(0, 0, Color.WHITE, w, h,
                    tinted ? new Color(0xEEE6FF) : new Color(0xFFFCFF)));
            g.fillRoundRect(1, 1, w - 3, h - 6, 22, 22);
            g.setColor(new Color(0xE5DAF6));
            g.drawRoundRect(1, 1, w - 3, h - 6, 22, 22);
            g.dispose(); super.paintComponent(source);
        }
    }

    static final class CardStage extends Surface {
        CardStage() { super(null, true); }
        @Override public void doLayout() {
            if (getComponentCount() == 0) return;
            int h = Math.max(40, Math.min(190, getHeight()-24));
            int w = Math.max(32, Math.min(getWidth()-24, h*150/190));
            getComponent(0).setBounds((getWidth()-w)/2, (getHeight()-h)/2, w, h);
        }
    }

    static final class ActionButton extends JButton {
        private final boolean primary;
        ActionButton(String text, boolean primary) {
            super(text); this.primary = primary;
            setOpaque(false); setContentAreaFilled(false); setBorderPainted(false);
            setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            setBorder(new EmptyBorder(12, 16, 12, 16));
            setPreferredSize(new Dimension(180, 46));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        @Override protected void paintComponent(Graphics source) {
            Graphics2D g = graphics(source);
            Color from = !isEnabled() ? new Color(0xEEE8F5) : primary ? new Color(0x8250E8) : Color.WHITE;
            Color to = !isEnabled() ? new Color(0xE7E0EF) : primary ? new Color(0x6425C8) : new Color(0xF5F0FF);
            if (isEnabled() && getModel().isRollover()) from = from.brighter();
            int y = getModel().isPressed() ? 3 : 1;
            g.setPaint(new GradientPaint(0, 0, from, getWidth(), getHeight(), to));
            g.fillRoundRect(1, y, getWidth()-3, getHeight()-4, 16, 16);
            g.setColor(new Color(0xD6C5EF));
            g.drawRoundRect(1, y, getWidth()-3, getHeight()-4, 16, 16);
            g.setColor(!isEnabled() ? new Color(0x655478) : primary ? Color.WHITE : AppTheme.PRIMARY);
            fitFont(g, getText(), 14, getWidth()-24);
            g.drawString(getText(), (getWidth()-g.getFontMetrics().stringWidth(getText()))/2,
                    (getHeight()-g.getFontMetrics().getHeight())/2+g.getFontMetrics().getAscent());
            if (hasFocus()) {
                g.setColor(AppTheme.PRIMARY); g.setStroke(new BasicStroke(2));
                g.drawRoundRect(4, 4, getWidth()-9, getHeight()-9, 12, 12);
            }
            g.dispose();
        }
    }

    static final class ItemView extends JPanel {
        private final String name, subtitle, status;
        private final Color accent;
        private final boolean title, selected, equipped, focused;
        ItemView(String name, String subtitle, String status, Color accent,
                 boolean title, boolean selected, boolean equipped, boolean focused) {
            this.name=name; this.subtitle=subtitle; this.status=status; this.accent=accent;
            this.title=title; this.selected=selected; this.equipped=equipped; this.focused=focused;
            setOpaque(false);
            getAccessibleContext().setAccessibleName(name+", "+subtitle+", "+status);
        }
        @Override protected void paintComponent(Graphics source) {
            Graphics2D g=graphics(source);
            int w=getWidth(), h=getHeight();
            g.setPaint(new GradientPaint(0, 0, selected ? new Color(0xF0E7FF) : Color.WHITE,
                    w, h, selected ? new Color(0xE8E9FF) : new Color(0xFBF9FF)));
            g.fillRoundRect(2, 4, w-5, h-8, 18, 18);
            g.setColor(selected || focused ? new Color(0xA78BDA) : new Color(0xEEE7F5));
            g.setStroke(new BasicStroke(focused ? 2 : 1));
            g.drawRoundRect(2, 4, w-5, h-8, 18, 18);
            g.setColor(new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),24));
            g.fillRoundRect(16, (h-46)/2, 46, 46, 14, 14);
            drawEmblem(g, 39, h/2, 13, accent, title);
            int statusWidth=0;
            g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,11));
            if(w>420) {
                statusWidth=g.getFontMetrics().stringWidth(status)+24;
                g.setColor(equipped ? new Color(0xE0F4EE) : new Color(0xF3EDF9));
                g.fillRoundRect(w-statusWidth-16,(h-26)/2,statusWidth,26,13,13);
                g.setColor(equipped ? AppTheme.SECONDARY : AppTheme.PRIMARY);
                g.drawString(status,w-statusWidth-4,h/2+4);
            }
            String label=shorten(g,name,15,w-94-(statusWidth==0?0:statusWidth+20));
            g.setColor(AppTheme.TEXT);g.drawString(label,76,h/2-5);
            g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12));g.setColor(AppTheme.MUTED);
            g.drawString(subtitle+(statusWidth==0?" · "+status:""),76,h/2+16);
            g.dispose();
        }
    }

    static final class TitlePreview extends JPanel {
        private final String username, title;
        private final boolean gold;
        private double phase;
        TitlePreview(String username, String title, boolean gold) {
            this.username=username.isBlank()?"Tên của bạn":username;
            this.title=title.replaceFirst("^Danh hiệu\\s+", ""); this.gold=gold;
            setOpaque(false);setPreferredSize(new Dimension(300,300));
            getAccessibleContext().setAccessibleName("Xem trước: "+this.username+", "+this.title);
        }
        void setPhase(double phase) { this.phase=phase; repaint(); }
        @Override protected void paintComponent(Graphics source) {
            Graphics2D g=graphics(source);
            int w=getWidth(), h=getHeight();
            if(w<1 || h<1){g.dispose();return;}
            g.clip(new RoundRectangle2D.Double(0,0,w,h,22,22));
            Color accent=gold ? new Color(0xAA6A10) : new Color(0x7041CB);
            Color pale=gold ? new Color(0xFFF0CC) : new Color(0xE9DEFF);
            g.setPaint(new GradientPaint(0,0,new Color(0xFCFAFF),w,h,pale));g.fillRect(0,0,w,h);
            int cx=w/2, cy=h/2;
            g.setPaint(new RadialGradientPaint(new Point2D.Float(cx,cy-20),Math.max(w,h)*0.6f,
                    new float[]{0,1}, new Color[]{new Color(255,255,255,235),new Color(255,255,255,0)}));
            g.fillRect(0,0,w,h);
            // Sparkles stay near the edges, away from the player name and title.
            for(int i=0;i<14;i++) {
                double t=phase*Math.PI*2+i*1.7;
                int x=(i%2==0?16:w-22)+(int)(8*Math.sin(t*0.5));
                int y=18+(i*47)%Math.max(1,h-36);
                float alpha=(float)(0.18+0.38*(0.5+0.5*Math.sin(t)));
                g.setComposite(AlphaComposite.SrcOver.derive(alpha));g.setColor(accent);
                sparkle(g,x,y,2+(i%3));
            }
            g.setComposite(AlphaComposite.SrcOver);
            int cardW=Math.max(60,Math.min(w-32,290)), cardH=Math.max(60,Math.min(h-24,246));
            int x=(w-cardW)/2, y=(h-cardH)/2;
            g.setColor(new Color(80,40,130,12));g.fillRoundRect(x,y+6,cardW,cardH,24,24);
            g.setPaint(new GradientPaint(x,y,new Color(255,255,255,248),x+cardW,y+cardH,new Color(255,255,255,200)));
            g.fillRoundRect(x,y,cardW,cardH,24,24);
            g.setColor(new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),55));
            g.drawRoundRect(x,y,cardW-1,cardH-1,24,24);
            // A round medal, distinct from the rectangular playing-card preview.
            int medalY=y+cardH/4;
            int radius=Math.max(18,Math.min(29,cardH/7));
            float pulse=(float)(0.5+0.5*Math.sin(phase*Math.PI*2));
            for(int r=radius+10;r>radius;r-=2){g.setColor(new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),8+(int)(pulse*5)));g.fillOval(cx-r,medalY-r,2*r,2*r);}
            g.setPaint(new GradientPaint(cx-radius,medalY-radius,gold?new Color(0xF8D67D):new Color(0xA88AEE),cx+radius,medalY+radius,accent));
            g.fillOval(cx-radius,medalY-radius,2*radius,2*radius);
            g.setColor(new Color(255,255,255,170));g.drawOval(cx-radius+4,medalY-radius+4,2*radius-8,2*radius-8);
            drawEmblem(g,cx,medalY,radius/2,Color.WHITE,true);
            centered(g,username,cx,y+(int)(cardH*0.52),cardH<180?15:19,AppTheme.TEXT,cardW-28);
            int pillY=y+(int)(cardH*0.60), pillW=cardW-28, pillH=Math.max(24,(int)(cardH*0.14));
            g.setColor(pale);g.fillRoundRect(cx-pillW/2,pillY,pillW,pillH,17,17);
            centered(g,title,cx,pillY+pillH/2+5,cardH<180?12:14,accent,pillW-16);
            centered(g,"Danh hiệu của bạn",cx,y+(int)(cardH*0.92),10,AppTheme.MUTED,cardW-20);
            g.dispose();
        }
    }

    static void centered(Graphics2D g,String text,int cx,int baseline,int size,Color color,int width) {
        fitFont(g,text,size,width);g.setColor(color);
        g.drawString(text,cx-g.getFontMetrics().stringWidth(text)/2,baseline);
    }
    static void fitFont(Graphics2D g,String text,int size,int width) {
        do {g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,size--));}
        while(size>=10 && g.getFontMetrics().stringWidth(text)>width);
    }
    static String shorten(Graphics2D g,String text,int size,int width) {
        g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,size));
        if(g.getFontMetrics().stringWidth(text)<=width)return text;
        while(!text.isEmpty() && g.getFontMetrics().stringWidth(text+"…")>width)text=text.substring(0,text.length()-1);
        return text+"…";
    }
    static void sparkle(Graphics2D g,int x,int y,int r) {
        Path2D p=new Path2D.Double();p.moveTo(x,y-r);p.lineTo(x+1,y-1);p.lineTo(x+r,y);p.lineTo(x+1,y+1);
        p.lineTo(x,y+r);p.lineTo(x-1,y+1);p.lineTo(x-r,y);p.lineTo(x-1,y-1);p.closePath();g.fill(p);
    }
    static void drawEmblem(Graphics2D g,int cx,int cy,int r,Color color,boolean title) {
        g.setColor(color);
        if(title){
            Path2D star=new Path2D.Double();
            for(int i=0;i<10;i++){double angle=-Math.PI/2+i*Math.PI/5;double radius=i%2==0?r:r*0.46;
                double x=cx+Math.cos(angle)*radius,y=cy+Math.sin(angle)*radius;
                if(i==0)star.moveTo(x,y);else star.lineTo(x,y);}
            star.closePath();g.fill(star);
        }else{
            g.setStroke(new BasicStroke(1.7f));g.drawRoundRect(cx-r+3,cy-r,r*2-6,r*2,5,5);
            sparkle(g,cx,cy,5);
        }
    }
}
