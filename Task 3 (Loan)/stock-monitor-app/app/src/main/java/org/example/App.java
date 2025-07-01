package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import yahoofinance.Stock;
import yahoofinance.YahooFinance;

// Simple JavaFX app to monitor and plot Dow Jones stock prices
public class App extends Application {

    // Queue to keep track of all fetched stock prices
    private static final LinkedBlockingQueue<StockDataEntry> stockDataQueue = new LinkedBlockingQueue<>();

    private static final String DOW_JONES_SYMBOL = "^DJI";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private XYChart.Series<String, Number> series;
    private ScheduledExecutorService executor;

    // Represents a single stock price at a specific time
    static class StockDataEntry {
        private final BigDecimal price;
        private final String timestamp;

        public StockDataEntry(BigDecimal price, String timestamp) {
            this.price = price;
            this.timestamp = timestamp;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public String getTimestamp() {
            return timestamp;
        }
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Dow Jones Live Stock Monitor");

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Time");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Price (USD)");

        LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("Dow Jones Industrial Average");
        lineChart.setAnimated(false);

        series = new XYChart.Series<>();
        series.setName("^DJI");
        lineChart.getData().add(series);

        VBox root = new VBox(lineChart);
        Scene scene = new Scene(root, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.show();

        startStockMonitoring();
    }

    // Fetches stock data in the background and updates the chart
    private void startStockMonitoring() {
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(new Runnable() {
            private int backoffSeconds = 0;
            @Override
            public void run() {
                if (backoffSeconds > 0) {
                    backoffSeconds -= 60;
                    if (backoffSeconds < 0) backoffSeconds = 0;
                    return;
                }
                try {
                    Stock dowJones = YahooFinance.get(DOW_JONES_SYMBOL);
                    if (dowJones != null && dowJones.getQuote() != null) {
                        BigDecimal currentPrice = dowJones.getQuote().getPrice();
                        String currentTimestamp = LocalDateTime.now().format(TIME_FORMATTER);
                        StockDataEntry dataEntry = new StockDataEntry(currentPrice, currentTimestamp);
                        stockDataQueue.offer(dataEntry);
                        updateChart();
                        showStatus("");
                    }
                } catch (IOException e) {
                    if (e.getMessage() != null && e.getMessage().contains("429")) {
                        // If we get rate-limited, pause updates for 5 minutes
                        backoffSeconds = 300;
                        showStatus("Rate limit reached. Pausing updates for 5 minutes.");
                    } else {
                        showStatus("Error fetching stock data: " + e.getMessage());
                    }
                }
            }
        }, 0, 60, TimeUnit.SECONDS); // Check every 60 seconds
    }

    // Refreshes the chart with all stored data
    private void updateChart() {
        Platform.runLater(() -> {
            series.getData().clear();
            for (StockDataEntry entry : stockDataQueue) {
                series.getData().add(new XYChart.Data<>(entry.getTimestamp(), entry.getPrice()));
            }
        });
    }

    // Updates the window title with status info
    private void showStatus(String message) {
        Platform.runLater(() -> {
            Stage stage = (Stage) series.getChart().getScene().getWindow();
            if (stage != null) {
                if (!message.isEmpty()) {
                    stage.setTitle("Dow Jones Live Stock Monitor - " + message);
                } else {
                    stage.setTitle("Dow Jones Live Stock Monitor");
                }
            }
        });
    }

    @Override
    public void stop() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}