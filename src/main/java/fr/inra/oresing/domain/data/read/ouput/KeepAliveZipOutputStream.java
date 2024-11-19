package fr.inra.oresing.domain.data.read.ouput;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.ZipOutputStream;

public class KeepAliveZipOutputStream extends ZipOutputStream {
    private final Timer timer;
    private TimerTask currentTask;
    private static final long INACTIVITY_DELAY = 50000; // 50 secondes en millisecondes

    public KeepAliveZipOutputStream(OutputStream out) {
        super(out, StandardCharsets.UTF_8);
        this.timer = new Timer(true); // Créer un timer en tant que thread daemon
        scheduleKeepAliveTask();
    }

    private synchronized void scheduleKeepAliveTask() {
        if (currentTask != null) {
            currentTask.cancel();
        }
        currentTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    sendKeepAliveBit();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        timer.schedule(currentTask, INACTIVITY_DELAY);
    }

    private void sendKeepAliveBit() throws IOException {
        // Envoyer un bit non significatif
        // Ici, nous écrivons un octet nul, qui sera compressé efficacement
        super.write(0);
        super.flush();
    }

    @Override
    public void write(int b) throws IOException {
        super.write(b);
        scheduleKeepAliveTask();
    }

    @Override
    public void write(byte[] b) throws IOException {
        super.write(b);
        scheduleKeepAliveTask();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        super.write(b, off, len);
        scheduleKeepAliveTask();
    }

    @Override
    public void close() throws IOException {
        timer.cancel(); // Arrêter le timer avant de fermer le flux
        super.close();
    }
}