package me.mdbell.noexs.ui.controllers;

import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import me.mdbell.javafx.control.AddressSpinner;
import me.mdbell.javafx.control.HexSpinner;
import me.mdbell.noexs.ui.Settings;
import me.mdbell.noexs.ui.services.PointerSearchResult;
import me.mdbell.noexs.ui.services.PointerSearchService;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;

public class PointerSearchController implements IController {

    @FXML
    AddressSpinner addressSpinner;

    @FXML
    Spinner<Integer> depthSpinner;

    @FXML
    Spinner<Integer> threadsSpinner;

    @FXML
    HexSpinner offsetSpinner;

    @FXML
    Button dumpFileButton;

    @FXML
    TextField dumpFilePath;

    @FXML
    Button indexFileButton;

    @FXML
    TextField indexFilePath;

    @FXML
    TextField resultText;

    @FXML
    ListView<PointerSearchResult> resultList;

    @FXML
    Button searchButton;

    @FXML
    Button cancelButton;

    @FXML
    AddressSpinner filterMaxAddress;
    @FXML
    AddressSpinner filterMinAddress;

    @FXML
    CheckBox filterCheckbox;

    @FXML
    AddressSpinner relativeAddress;

    private List<PointerSearchResult> unfilteredResults = new ArrayList<>();

    private ObservableList<PointerSearchResult> results;

    private MainController mc;

    private FileChooser fileChooser = new FileChooser();

    private final PointerSearchService searchService = new PointerSearchService();

    @FXML
    public void initialize() {
        fileChooser.setInitialDirectory(new File("./"));
        depthSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10));

        threadsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, Runtime.getRuntime().availableProcessors()));
        threadsSpinner.getValueFactory().setValue(Settings.getPointerThreadCount());

        depthSpinner.getValueFactory().setValue(Settings.getPointerDepth());
        offsetSpinner.getValueFactory().setValue(Settings.getPointerOffset());

        resultList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue != null) {
                resultText.setText(newValue.formatted(relativeAddress.getValue()));
            }else{
                resultText.setText("");
            }
        });

        resultList.setCellFactory(param -> new ListCell<>(){
            @Override
            protected void updateItem(PointerSearchResult item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.formatted(relativeAddress.getValue()));
                }
            }
        });

        indexFilePath.textProperty().addListener((observable, oldValue, newValue) -> updateSearchButton());
        dumpFilePath.textProperty().addListener((observable, oldValue, newValue) -> updateSearchButton());

        searchService.messageProperty().addListener((observable, oldValue, newValue) -> mc.setStatus(newValue));

        results = FXCollections.observableArrayList();
        resultList.setItems(results);

        relativeAddress.valueProperty().addListener((observable, oldValue, newValue) -> updateFilter());

        filterCheckbox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            filterMaxAddress.setDisable(!newValue);
            filterMinAddress.setDisable(!newValue);
            updateFilter();
        });

        filterMaxAddress.valueProperty().addListener((observable, oldValue, newValue) -> updateFilter());
        filterMinAddress.valueProperty().addListener((observable, oldValue, newValue) -> updateFilter());
    }

    private void updateFilter() {
        results.clear();
        long min = filterMinAddress.getValue();
        long max = filterMaxAddress.getValue();
        if(filterCheckbox.isSelected()) {
            List<PointerSearchResult> filtered = new ArrayList<>();
            for (PointerSearchResult result : unfilteredResults) {
                long addr = result.getAddress();
                if (addr <= max && addr >= min) {
                    filtered.add(result);
                }
            }
            results.addAll(filtered);
        }else{
            results.addAll(unfilteredResults);
        }
    }

    private void updateSearchButton() {
        String index = indexFilePath.getText();
        String dump = indexFilePath.getText();
        searchButton.setDisable(index.length() == 0 || dump.length() == 0);
    }

    @Override
    public void setMainController(MainController c) {
        this.mc = c;
    }

    public void onBrowseDumpFile(ActionEvent event) {
        browseFile(dumpFilePath.textProperty(), "Please select a memory dump", "Memory Dump Files", "*.dat");
    }

    public void onBrowseIndexFile(ActionEvent event) {
        browseFile(indexFilePath.textProperty(), "Please select an index file", "Memory Dump Index Files", "*.xml");
    }

    public void onSearchAction(ActionEvent event) {
        searchService.setDumpPath(Paths.get(dumpFilePath.getText()));
        searchService.setIndexPath(Paths.get(indexFilePath.getText()));
        searchService.setMaxDepth(depthSpinner.getValue());
        searchService.setMaxOffset(offsetSpinner.getValue());
        searchService.setAddress(addressSpinner.getValue());
        searchService.setThreadCount(threadsSpinner.getValue());

        searchService.setOnFailed(event1 -> {
            mc.setStatus("Search Failed!");
            event1.getSource().getException().printStackTrace();
            toggleInput(false);
        });

        searchService.setOnSucceeded(event1 -> {
            Set<PointerSearchResult>results = (Set<PointerSearchResult>) event1.getSource().getValue();
            this.unfilteredResults.clear();
            this.unfilteredResults.addAll(results);
            mc.setStatus("Search Completed!");
            toggleInput(false);
            updateFilter();
        });

        mc.getProgressBar().progressProperty().bind(searchService.progressProperty());
        searchService.restart();

        toggleInput(true);
    }

    public void onCancelAction(ActionEvent event) {
        if (searchService.cancel()) {
            toggleInput(false);
        }
    }

    @Override
    public void onConnect() {

    }

    @Override
    public void onDisconnect() {

    }

    @Override
    public void stop() {
        Settings.setPointerOffset(offsetSpinner.getValue());
        Settings.setPointerDepth(depthSpinner.getValue());
        Settings.setPointerThreadCount(threadsSpinner.getValue());
    }

    private void toggleInput(boolean disabled) {
        addressSpinner.setDisable(disabled);
        depthSpinner.setDisable(disabled);
        threadsSpinner.setDisable(disabled);
        offsetSpinner.setDisable(disabled);
        dumpFileButton.setDisable(disabled);
        indexFileButton.setDisable(disabled);
        searchButton.setDisable(disabled);
        cancelButton.setDisable(!disabled);
    }

    private void browseFile(StringProperty property, String title, String desc, String... extensions) {
        fileChooser.setTitle(title);
        List<FileChooser.ExtensionFilter> filterList = fileChooser.getExtensionFilters();
        filterList.clear();
        filterList.add(new FileChooser.ExtensionFilter(desc, extensions));

        File f = fileChooser.showOpenDialog(mc.getStage());

        if (f != null) {
            fileChooser.setInitialDirectory(f.getParentFile());
            property.setValue(f.toPath().toString());
        }
    }

    public void setFilterMin(long address) {
        filterMinAddress.getValueFactory().setValue(address);
    }

    public void setFilterMax(long address) {
        filterMaxAddress.getValueFactory().setValue(address);
    }

    public void setRelativeAddress(long address) {
        relativeAddress.getValueFactory().setValue(address);
    }
}
