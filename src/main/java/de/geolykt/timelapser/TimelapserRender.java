package de.geolykt.timelapser;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class TimelapserRender implements Runnable {

    private final Timelapser extension;
    private BufferedImage bi;
    private JFrame jf = new JFrame("Timelapser Render View");
    private JLabel imageLabel = new JLabel();
    private boolean foo = false;

    public TimelapserRender(Timelapser extension) {
        this.extension = extension;
    }

    @Override
    public void run() {
        bi = new BufferedImage((int) Timelapser.WIDTH,(int) Timelapser.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = bi.createGraphics();
        g2d.setBackground(Color.BLACK);
        File root = new File("timelapser");
        File session = new File(root, Long.toString(System.currentTimeMillis()));
        session.mkdirs();
        int counter = 0;
        BufferedImage frameImage = new BufferedImage((int) Timelapser.WIDTH,(int) Timelapser.HEIGHT, BufferedImage.TYPE_INT_RGB);
        WritableRaster frameImageRaster = null;
        while (true) {
            if (extension.isHaltingRendering()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            }
            frameImageRaster = bi.copyData(frameImageRaster);
            frameImage.setData(frameImageRaster);
            frameImage.setData(bi.copyData(null));
            imageLabel.setIcon(new ImageIcon(frameImage));
            if (!foo) {
                foo = true;
                JPanel panel = new JPanel(true); // For the flicker-free updates
                panel.add(imageLabel);
                jf.add(panel);
                jf.pack();
                jf.setVisible(true);
            }
            try {
                extension.lock.acquire();
            } catch (InterruptedException e) {
                extension.lock.drainPermits();
                e.printStackTrace(); // InterruptedExceptions are not used for control flow here, so this is the work of something else
            }
            g2d.clearRect(0, 0, (int) Timelapser.WIDTH,(int) Timelapser.HEIGHT);
            for (Renderable r : extension.renderData) {
                r.render(g2d);
            }
            File image = new File(session, Integer.toString(counter++) + ".png");
            try {
                image.createNewFile();
                ImageIO.write(bi, "png", image);
            } catch (IOException e) {
                e.printStackTrace();
            }
            extension.awaitInput.set(true);
        }
    }

}
