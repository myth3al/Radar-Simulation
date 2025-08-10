package com.example.radar;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Random;

import javax.swing.JFrame;
import javax.swing.Timer;
import javax.swing.WindowConstants;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.knowm.xchart.SwingWrapper;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.style.Styler;

public class RadarSimulator {

    private static final double SAMPLING_RATE = 10e6;   // 10 MHz
    private static final double PULSE_WIDTH = 20e-6;    // 20 us
    private static final double CARRIER_FREQ = 1e6;     // 1 MHz carrier
    private static final double CHIRP_BW = 2e6;         // 2 MHz chirp bandwidth
    private static final double SPEED_OF_LIGHT = 3e8;   // m/s

    private static final double TARGET_RANGE = 1500;    // meters
    private static final double TARGET_REFLECTION = 0.8;// echo amplitude factor
    private static final double NOISE_STD = 0.1;        // noise std dev

    private static final int PULSE_SAMPLES = (int) (PULSE_WIDTH * SAMPLING_RATE);
    private static final int RX_LENGTH = PULSE_SAMPLES * 4;

    private static double[] txPulse;
    private static double[] timeTxUs;
    private static double[] timeRxUs;
    private static double[] rangeMeters;

    private static double[] rxSignal;
    private static double[] compressedSignal;

    private static XYChart chart;
    private static SwingWrapper<XYChart> swingWrapper;

    private static int viewIndex = 0; // 0=Tx,1=Rx,2=Compressed,3=Combined

    public static void main(String[] args) {

        txPulse = generateLFMChirp(PULSE_SAMPLES, SAMPLING_RATE, CARRIER_FREQ, CHIRP_BW);

        timeTxUs = new double[PULSE_SAMPLES];
        for (int i = 0; i < PULSE_SAMPLES; i++) {
            timeTxUs[i] = i / SAMPLING_RATE * 1e6;
        }

        timeRxUs = new double[RX_LENGTH];
        for (int i = 0; i < RX_LENGTH; i++) {
            timeRxUs[i] = i / SAMPLING_RATE * 1e6;
        }

        rangeMeters = new double[RX_LENGTH];
        for (int i = 0; i < RX_LENGTH; i++) {
            double timeSec = i / SAMPLING_RATE;
            rangeMeters[i] = timeSec * SPEED_OF_LIGHT / 2;
        }

        // Initial received and compressed signals (empty)
        rxSignal = new double[RX_LENGTH];
        compressedSignal = new double[RX_LENGTH];

        chart = createChart("Radar Visualization - Transmit Pulse", "Time (µs)", "Amplitude");

        // Start with transmit pulse data
        chart.addSeries("Transmit Pulse", timeTxUs, txPulse);

        swingWrapper = new SwingWrapper<>(chart);
        JFrame frame = swingWrapper.displayChart();

        // Key listener to cycle views with left/right arrows
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
                    viewIndex = (viewIndex + 1) % 4;
                    updateView();
                } else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                    viewIndex = (viewIndex + 3) % 4;  // +3 mod4 to cycle backward
                    updateView();
                }
            }
        });

        // Update loop to simulate received signal & compress pulse live
        Timer timer = new Timer(100, e -> {
            rxSignal = simulateReceivedSignal(txPulse, RX_LENGTH, SAMPLING_RATE, TARGET_RANGE, TARGET_REFLECTION);
            compressedSignal = matchedFilter(txPulse, rxSignal);
            updateView();
        });
        timer.start();

        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    }

    private static void updateView() {
        switch (viewIndex) {
            case 0: // Transmit pulse
                chart.setTitle("Radar Visualization - Transmit Pulse");
                chart.getSeriesMap().clear();
                chart.addSeries("Transmit Pulse", timeTxUs, txPulse);
                chart.setXAxisTitle("Time (µs)");
                chart.setYAxisTitle("Amplitude");
                break;
            case 1: // Received signal
                chart.setTitle("Radar Visualization - Received Signal (Echo + Noise)");
                chart.getSeriesMap().clear();
                chart.addSeries("Received Signal", timeRxUs, rxSignal);
                chart.setXAxisTitle("Time (µs)");
                chart.setYAxisTitle("Amplitude");
                break;
            case 2: // Compressed pulse
                chart.setTitle("Radar Visualization - Pulse Compression Output");
                chart.getSeriesMap().clear();
                chart.addSeries("Compressed Pulse", rangeMeters, compressedSignal);
                chart.setXAxisTitle("Range (m)");
                chart.setYAxisTitle("Amplitude");
                break;
            case 3: // Combined all
                chart.setTitle("Radar Visualization - Combined Signals");
                chart.getSeriesMap().clear();
                chart.addSeries("Transmit Pulse", timeTxUs, txPulse);
                chart.addSeries("Received Signal", timeRxUs, rxSignal);
                chart.addSeries("Compressed Pulse", rangeMeters, compressedSignal);
                chart.setXAxisTitle("Time (µs) / Range (m)");
                chart.setYAxisTitle("Amplitude");
                break;
        }
        swingWrapper.repaintChart();
    }

    private static XYChart createChart(String title, String xLabel, String yLabel) {
        XYChart chart = new XYChartBuilder()
                .width(900)
                .height(500)
                .title(title)
                .xAxisTitle(xLabel)
                .yAxisTitle(yLabel)
                .build();
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setMarkerSize(2);
        return chart;
    }

    private static double[] generateLFMChirp(int samples, double fs, double f0, double bandwidth) {
        double[] waveform = new double[samples];
        double pulseDuration = samples / fs;
        double k = bandwidth / pulseDuration;
        for (int i = 0; i < samples; i++) {
            double t = i / fs;
            double phase = 2 * Math.PI * (f0 * t + 0.5 * k * t * t);
            waveform[i] = Math.sin(phase);
        }
        return waveform;
    }

    private static double[] simulateReceivedSignal(double[] txPulse, int rxLength, double fs,
                                                   double targetRange, double targetReflection) {
        double[] rx = new double[rxLength];
        Arrays.fill(rx, 0);

        int pulseSamples = txPulse.length;
        int delaySamples = (int) ((2 * targetRange / SPEED_OF_LIGHT) * fs);

        for (int i = 0; i < pulseSamples; i++) {
            int idx = i + delaySamples;
            if (idx < rxLength) {
                rx[idx] += txPulse[i] * targetReflection;
            }
        }

        Random rand = new Random();
        for (int i = 0; i < rxLength; i++) {
            rx[i] += NOISE_STD * rand.nextGaussian();
        }

        return rx;
    }

    private static double[] matchedFilter(double[] txPulse, double[] rxSignal) {
        int n = Integer.highestOneBit(rxSignal.length) << 1;
        if (n < rxSignal.length * 2) {
            n <<= 1;
        }

        double[] txPadded = new double[n];
        double[] rxPadded = new double[n];
        System.arraycopy(txPulse, 0, txPadded, 0, txPulse.length);
        System.arraycopy(rxSignal, 0, rxPadded, 0, rxSignal.length);

        FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);

        Complex[] RX = fft.transform(rxPadded, TransformType.FORWARD);

        Complex[] TX = fft.transform(txPadded, TransformType.FORWARD);
        for (int i = 0; i < TX.length; i++) {
            TX[i] = TX[i].conjugate();
        }

        Complex[] compressed = new Complex[n];
        for (int i = 0; i < n; i++) {
            compressed[i] = RX[i].multiply(TX[i]);
        }

        Complex[] ifft = fft.transform(compressed, TransformType.INVERSE);

        double[] result = new double[n];
        for (int i = 0; i < n; i++) {
            result[i] = ifft[i].abs();
        }

        return result;
    }
}
