package cam;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

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

    static {
        System.load("/home/wes/TIMEVIBE/opencv-3.4.13/build/lib/libopencv_java3413.so");
    }

    private static final int STREAM_PORT = 5000; // to viewers
    private static final int BOARD_PORT = 5050;  // from board sender
    private static final AtomicReference<BufferedImage> boardFrame = new AtomicReference<>(null);
    private static final List<Socket> clients = Collections.synchronizedList(new ArrayList<>());
    static JFrame frame1 = new JFrame("Bang Stream Viewer");
    static JLabel imageLabel = new JLabel();
    public static void main(String[] args) {
		frame1.getContentPane().add(imageLabel, BorderLayout.CENTER);
	    frame1.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	    frame1.setSize(640, 480);
	    frame1.setVisible(true);
        System.out.println("WebcamServer started.");
        VideoCapture camera = new VideoCapture(0);

        // Thread to stream webcam/board frames
        new Thread(() -> streamLoop(camera)).start();

        // Thread to listen for board sender
        new Thread(WebcamServer::receiveBoardFrames).start();
    }

    /** Accept viewers (WebcamUpdater) and stream frames */
    private static void streamLoop(VideoCapture camera) {
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

    private static void handleClient(VideoCapture camera, Socket client) {
        try (DataOutputStream dos = new DataOutputStream(client.getOutputStream())) {
            Mat frame = new Mat();
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
                	
                    if (!camera.isOpened() || !camera.read(frame)) continue;
                    MatOfByte mob = new MatOfByte();
                    if (!Imgcodecs.imencode(".jpg", frame, mob)) continue;
                    jpegBytes = mob.toArray();
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
