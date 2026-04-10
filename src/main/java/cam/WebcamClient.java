package cam;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.Socket;

public class WebcamClient implements Runnable {
	private final String serverAddress;
	private final int port;
	private BufferedImage image;

	public WebcamClient(String serverAddress, int port) {
		this.serverAddress = serverAddress;
		this.port = port;
	}

	@Override
	public void run() {
		//JFrame frame = new JFrame("Webcam Stream");
		//JLabel label = new JLabel();
		//frame.getContentPane().add(label, BorderLayout.CENTER);
		//frame.setSize(640, 480);
		//frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		//frame.setVisible(true);

		try (Socket socket = new Socket(serverAddress, port); InputStream inputStream = socket.getInputStream()) {

			System.out.println("Connected to server: " + serverAddress + ":" + port);

			byte[] buffer = new byte[65536]; // Adjust buffer size as needed
			int bytesRead;
			while ((bytesRead = inputStream.read(buffer)) != -1) {
				ByteArrayInputStream bais = new ByteArrayInputStream(buffer, 0, bytesRead);
				image = ImageIO.read(bais);
				if (getImage() != null) {
				//	label.setIcon(new ImageIcon(getImage()));
				//	frame.repaint();
				}
			}
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
		}
	}

	public static void main(String[] args) {
		String serverAddress = "localhost";
		int port = 5000;

		Thread clientThread = new Thread(new WebcamClient(serverAddress, port));
		clientThread.start();
	}

	public BufferedImage getImage() {
		return image;
	}
}
