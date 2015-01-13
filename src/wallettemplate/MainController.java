package wallettemplate;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.DownloadProgressTracker;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.utils.MonetaryFormat;
import org.coinjoin.client.MixStart;

import com.subgraph.orchid.TorClient;
import com.subgraph.orchid.TorInitializationListener;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.layout.HBox;
import javafx.util.Callback;
import javafx.util.Duration;
import javafx.util.StringConverter;

import org.fxmisc.easybind.EasyBind;

import wallettemplate.controls.ClickableBitcoinAddress;
import wallettemplate.controls.NotificationBarPane;
import wallettemplate.utils.BitcoinUIModel;
import wallettemplate.utils.easing.EasingMode;
import wallettemplate.utils.easing.ElasticInterpolator;
import static wallettemplate.Main.bitcoin;

/**
 * Gets created auto-magically by FXMLLoader via reflection. The widget fields are set to the GUI controls they're named
 * after. This class handles all the updates and event handling for the main UI.
 */
public class MainController {
    public HBox controlsBox;
    public Label balance;
    public Button sendMoneyOutBtn;
    public ClickableBitcoinAddress addressControl;
    public ListView<TransactionOutput> outputSelect;
    public TextField destination;
    public Button newDestination, newChange, mixStart;
    public TextField change;
    public TextArea debugInfo;
    
    private MixStart mix;
    
    private Address destAddr, changeAddr;

    private BitcoinUIModel model = new BitcoinUIModel();
    private NotificationBarPane.Item syncItem;

    // Called by FXMLLoader.
    public void initialize() {
        addressControl.setOpacity(0.0);
    }

    public void onBitcoinSetup() {
        model.setWallet(bitcoin.wallet());
        addressControl.addressProperty().bind(model.addressProperty());
        balance.textProperty().bind(EasyBind.map(model.balanceProperty(), coin -> MonetaryFormat.BTC.noCode().format(coin).toString()));
        // Don't let the user click send money when the wallet is empty.
        sendMoneyOutBtn.disableProperty().bind(model.balanceProperty().isEqualTo(Coin.ZERO));

        TorClient torClient = Main.bitcoin.peerGroup().getTorClient();
        if (torClient != null) {
            SimpleDoubleProperty torProgress = new SimpleDoubleProperty(-1);
            String torMsg = "Initialising Tor";
            syncItem = Main.instance.notificationBar.pushItem(torMsg, torProgress);
            torClient.addInitializationListener(new TorInitializationListener() {
                @Override
                public void initializationProgress(String message, int percent) {
                    Platform.runLater(() -> {
                        syncItem.label.set(torMsg + ": " + message);
                        torProgress.set(percent / 100.0);
                    });
                }

                @Override
                public void initializationCompleted() {
                    Platform.runLater(() -> {
                        syncItem.cancel();
                        showBitcoinSyncMessage();
                    });
                }
            });
        } else {
            showBitcoinSyncMessage();
        }
        model.syncProgressProperty().addListener(x -> {
            if (model.syncProgressProperty().get() >= 1.0) {
                readyToGoAnimation();
                if (syncItem != null) {
                    syncItem.cancel();
                    syncItem = null;
                }
            } else if (syncItem == null) {
                showBitcoinSyncMessage();
            }
        });
        
        Bindings.bindContent(outputSelect.getItems(), model.getOutputs());
        
        outputSelect.setCellFactory(new Callback<ListView<TransactionOutput>, ListCell<TransactionOutput>>() {

			@Override
			public ListCell<TransactionOutput> call(
					ListView<TransactionOutput> param) {
				return new TextFieldListCell<TransactionOutput>(new StringConverter<TransactionOutput>() {

					@Override
					public String toString(TransactionOutput object) {
						return (object.getValue().toPlainString() + " BTC | " + object.getAddressFromP2PKHScript(object.getParams()));
					}

					@Override
					public TransactionOutput fromString(String string) {
						// TODO Auto-generated method stub
						return null;
					}
					
				});
			}
        	
        });
        
        destAddr = Main.bitcoin.wallet().currentReceiveAddress();
        changeAddr = Main.bitcoin.wallet().getChangeAddress();
        
        destination.setEditable(false);
        destination.setText(destAddr.toString());
        change.setEditable(false);
        change.setText(changeAddr.toString());
        
        debugInfo.setEditable(false);
        debugInfo.clear();
    }
    
    public void mixStart(ActionEvent event) {
    	
    	newDestination.disableProperty().set(true);
    	newChange.disableProperty().set(true);
    	mixStart.disableProperty().set(true);
    	
    	debugInfo.clear();
    	TransactionOutput inputBuilder = outputSelect.getSelectionModel().getSelectedItem();
    	if (inputBuilder == null){
    		debugInfo.appendText("[ERROR] No Selected Output!\n");
        	this.finishMix();
    	} else {
	    	
	    	StringProperty strProp = new SimpleStringProperty();
	    	strProp.setValue(debugInfo.getText());
	    	
	    	debugInfo.textProperty().bind(strProp);
	    	
	    	mix = new MixStart(this, strProp, inputBuilder, destAddr, changeAddr);
	    	
	    	Thread t = new Thread(mix);
	    	t.setDaemon(true);
	    	t.start();
    	}
    	
    }
    
    public void finishMix() {
    	debugInfo.textProperty().unbind();
    	newDestination.disableProperty().set(false);
    	newChange.disableProperty().set(false);
    	mixStart.disableProperty().set(false);
    }
    
    public void updateChange(ActionEvent event) {
    	changeAddr = Main.bitcoin.wallet().freshReceiveAddress();
    	change.setText(changeAddr.toString());
    }
    
    public void updateDest(ActionEvent event) {
    	destAddr = Main.bitcoin.wallet().freshReceiveAddress();
    	destination.setText(destAddr.toString());
    }

    private void showBitcoinSyncMessage() {
        syncItem = Main.instance.notificationBar.pushItem("Synchronising with the Bitcoin network", model.syncProgressProperty());
    }

    public void sendMoneyOut(ActionEvent event) {
        // Hide this UI and show the send money UI. This UI won't be clickable until the user dismisses send_money.
        Main.instance.overlayUI("send_money.fxml");
    }

    public void settingsClicked(ActionEvent event) {
        Main.OverlayUI<WalletSettingsController> screen = Main.instance.overlayUI("wallet_settings.fxml");
        screen.controller.initialize(null);
    }

    public void restoreFromSeedAnimation() {
        // Buttons slide out ...
        TranslateTransition leave = new TranslateTransition(Duration.millis(1200), controlsBox);
        leave.setByY(80.0);
        leave.play();
    }

    public void readyToGoAnimation() {
        // Buttons slide in and clickable address appears simultaneously.
        TranslateTransition arrive = new TranslateTransition(Duration.millis(1200), controlsBox);
        arrive.setInterpolator(new ElasticInterpolator(EasingMode.EASE_OUT, 1, 2));
        arrive.setToY(0.0);
        FadeTransition reveal = new FadeTransition(Duration.millis(1200), addressControl);
        reveal.setToValue(1.0);
        ParallelTransition group = new ParallelTransition(arrive, reveal);
        group.setDelay(NotificationBarPane.ANIM_OUT_DURATION);
        group.setCycleCount(1);
        group.play();
    }

    public DownloadProgressTracker progressBarUpdater() {
        return model.getDownloadProgressTracker();
    }
}
