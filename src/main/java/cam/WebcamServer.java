package cam;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class WebcamServer {

    // JavaCV grabber replaces OpenCV's VideoCapture -- no absolute-path System.load,
    // native libs are bundled cross-platform by javacv-platform. One shared grabber
    // (index 0 = default webcam); grabFrameJpeg() is synchronized so concurrent viewer
    // threads can't corrupt the single grabber/converter.
    private static final Java2DFrameConverter CONVERTER = new Java2DFrameConverter();

    private static final int STREAM_PORT = 5000; // to viewers
    private static final int BOARD_PORT = 5050;  // from board sender
    private static final AtomicReference<BufferedImage> boardFrame = new AtomicReference<>(null);
    private static final List<Socket> clients = Collections.synchronizedList(new ArrayList<>());
    static JFrame frame1 = new JFrame("Bang Stream Viewer");
    static JLabel imageLabel = new JLabel();
    public static void main(String[] args) throws Exception {
		frame1.getContentPane().add(imageLabel, BorderLayout.CENTER);
	    frame1.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	    frame1.setSize(640, 480);
	    frame1.setVisible(true);
        System.out.println("WebcamServer started.");
        OpenCVFrameGrabber camera = new OpenCVFrameGrabber(0);
        camera.start();

        // Thread to stream webcam/board frames
        new Thread(() -> streamLoop(camera)).start();

        // Thread to listen for board sender
        new Thread(WebcamServer::receiveBoardFrames).start();
    }

    /** Grab one webcam frame and JPEG-encode it. Synchronized: the grabber and the
     *  reused converter BufferedImage are not thread-safe, and JPEG encoding must
     *  finish before the next grab overwrites the frame. Returns null on failure. */
    private static synchronized byte[] grabFrameJpeg(OpenCVFrameGrabber camera) {
        try {
            Frame f = camera.grab();
            if (f == null) return null;
            BufferedImage img = CONVERTER.convert(f);
            if (img == null) return null;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if (!ImageIO.write(img, "jpg", baos)) return null;
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /** Accept viewers (WebcamUpdater) and stream frames */
    private static void streamLoop(OpenCVFrameGrabber camera) {
        try (ServerSocket serverSocket = new ServerSocket(STREAM_PORT)) {
            System.out.println("Streaming server running on port " + STREAM_PORT);

            while (true) {
                Socket client = serverSocket.accept();
                System.out.println("Viewer connected: " + client.getInetAddress());
                clients.add(client);

                new Thread(() -> handleClient(camera, client)).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(OpenCVFrameGrabber camera, Socket client) {
        try (DataOutputStream dos = new DataOutputStream(client.getOutputStream())) {
            while (true) {
                byte[] jpegBytes;
                BufferedImage board = boardFrame.getAndSet(null);

                if (board != null) {

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(board, "jpg", baos);
                    jpegBytes = baos.toByteArray();
                  //  System.out.println("Streaming board frame to client...");
                    imageLabel.setIcon(new ImageIcon(board));
                    frame1.pack();
                } else {

                    jpegBytes = grabFrameJpeg(camera);
                    if (jpegBytes == null) continue;
                    imageLabel.setIcon(new ImageIcon(ImageIO.read(new ByteArrayInputStream(jpegBytes))));
                    frame1.pack();
                }

                dos.writeInt(jpegBytes.length);
                dos.write(jpegBytes);
                dos.flush();

                Thread.sleep(33); // ~30 FPS
            }
        } catch (Exception e) {
            System.out.println("Client disconnected: " + e.getMessage());
        } finally {
            clients.remove(client);
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    

private static void receiveBoardFrames() {
    try (ServerSocket boardSocket = new ServerSocket(5050)) {
        System.out.println("Board input server running on port 5050");
        while (true) {
            Socket socket = boardSocket.accept();
            //System.out.println("Board sender connected: " + socket.getInetAddress());

            new Thread(() -> {
                try (DataInputStream dis = new DataInputStream(
                        new BufferedInputStream(socket.getInputStream()))) {

                    while (true) {
                       // System.out.println("Waiting for length...");
                        int length;
                        try {
                            length = dis.readInt();
                        } catch (EOFException eof) {
                           // System.out.println("Board sender disconnected cleanly.");
                            break;
                        }

                       // System.out.println("Expecting " + length + " bytes of image data...");
                        byte[] data = new byte[length];
                        dis.readFully(data);
                       // System.out.println("Received all " + length + " bytes");

                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                        
                        if (img != null) {
                            boardFrame.set(img);
                            //System.out.println("Decoded board frame " + img.getWidth() + "x" + img.getHeight());
                        } else {
                            System.out.println("Failed to decode image — ImageIO.read() returned null");
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Board sender error: " + e.getMessage());
                } finally {
                    try { socket.close(); } catch (IOException ignored) {}
                }
            }).start();
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
}

}
