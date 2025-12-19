package com.scansione;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainApp extends Application {

    private Webcam webcam;
    private Timeline previewLoop;
    private final ExecutorService decodeExecutor = Executors.newSingleThreadExecutor();
    private final ImageView cameraView = new ImageView();
    private final TextArea resultArea = new TextArea();
    private final Label statusLabel = new Label("In attesa della cattura.");
    private Button scanButton;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("Scansione webcam");
        cameraView.setFitWidth(640);
        cameraView.setFitHeight(480);
        cameraView.setPreserveRatio(true);
        cameraView.setSmooth(true);

        resultArea.setEditable(false);
        resultArea.setWrapText(true);
        resultArea.setPromptText("Risultato del QR o del codice a barre");

        scanButton = new Button("Scatta e decodifica");
        scanButton.setDisable(true);
        scanButton.setOnAction(event -> triggerScan());

        VBox controls = new VBox(8,
                statusLabel,
                new HBox(10, scanButton),
                new Label("Codice letto:"),
                resultArea);
        controls.setPadding(new Insets(10));

        Label instructions = new Label("Premi il pulsante per acquisire un frame dalla webcam e cercare QR, Code 39 o Interleaved 2 di 5.");
        instructions.setWrapText(true);
        instructions.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setCenter(cameraView);
        root.setBottom(controls);
        root.setTop(instructions);

        Scene scene = new Scene(root, 720, 640);
        stage.setScene(scene);
        stage.show();

        initializeCamera();
        stage.setOnCloseRequest(event -> stopCamera());
    }

    private void initializeCamera() {
        try {
            var webcams = Webcam.getWebcams();
            if (webcams.isEmpty()) {
                showErrorAndExit("Nessuna webcam rilevata.");
                return;
            }

            webcam = webcams.get(0);
            webcam.setViewSize(WebcamResolution.VGA.getSize());
            webcam.open(true);
            scanButton.setDisable(false);
            startPreviewLoop();
        } catch (Exception e) {
            showErrorAndExit("Impossibile inizializzare la webcam: " + e.getMessage());
        }
    }

    private void startPreviewLoop() {
        if (previewLoop != null) {
            previewLoop.stop();
        }

        previewLoop = new Timeline(new KeyFrame(Duration.millis(33), event -> updatePreview()));
        previewLoop.setCycleCount(Timeline.INDEFINITE);
        previewLoop.play();
    }

    private void updatePreview() {
        if (webcam != null && webcam.isOpen()) {
            BufferedImage frame = webcam.getImage();
            if (frame != null) {
                cameraView.setImage(SwingFXUtils.toFXImage(frame, null));
            }
        }
    }

    private void triggerScan() {
        if (webcam == null || !webcam.isOpen()) {
            statusLabel.setText("Webcam non disponibile.");
            return;
        }

        scanButton.setDisable(true);
        statusLabel.setText("Acquisizione immagine...");

        decodeExecutor.submit(() -> {
            BufferedImage frame = webcam.getImage();
            Result decoded = null;
            if (frame != null) {
                decoded = decode(frame);
            }

            final Result finalResult = decoded;
            Platform.runLater(() -> {
                if (finalResult != null) {
                    resultArea.setText(finalResult.getText());
                    statusLabel.setText("Codice riconosciuto: " + finalResult.getBarcodeFormat());
                } else {
                    statusLabel.setText("Nessun QR o codice valido trovato.");
                }
                scanButton.setDisable(false);
            });
        });
    }

    private Result decode(BufferedImage image) {
        try {
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(
                    BarcodeFormat.QR_CODE,
                    BarcodeFormat.CODE_39,
                    BarcodeFormat.ITF
            ));
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
            return new MultiFormatReader().decode(binaryBitmap, hints);
        } catch (NotFoundException e) {
            return null;
        }
    }

    private void stopCamera() {
        if (previewLoop != null) {
            previewLoop.stop();
        }
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
        }
        decodeExecutor.shutdownNow();
    }

    private void showErrorAndExit(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, message);
            alert.showAndWait();
            Platform.exit();
        });
    }

    @Override
    public void stop() throws Exception {
        stopCamera();
        super.stop();
    }
}
