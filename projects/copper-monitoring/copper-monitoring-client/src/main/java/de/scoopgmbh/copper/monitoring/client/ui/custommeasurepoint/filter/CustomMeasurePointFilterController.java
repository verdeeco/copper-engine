/*
 * Copyright 2002-2013 SCOOP Software GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.scoopgmbh.copper.monitoring.client.ui.custommeasurepoint.filter;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.util.Callback;
import de.scoopgmbh.copper.monitoring.client.adapter.GuiCopperDataProvider;
import de.scoopgmbh.copper.monitoring.client.form.FxmlController;
import de.scoopgmbh.copper.monitoring.client.form.filter.BaseFilterController;

public class CustomMeasurePointFilterController extends BaseFilterController<CustomMeasurePointFilterModel> implements Initializable, FxmlController {
	final CustomMeasurePointFilterModel model= new CustomMeasurePointFilterModel();
	
	private final GuiCopperDataProvider copperDataProvider;
	

    public CustomMeasurePointFilterController(GuiCopperDataProvider copperDataProvider) {
		super();
		this.copperDataProvider = copperDataProvider;
	}

	@FXML //  fx:id="measurePointChoice"
    private ComboBox<String> measurePointIdComboBox; // Value injected by FXMLLoader

    @FXML //  fx:id="measurePointText"
    private TextField measurePointText; // Value injected by FXMLLoader


    @Override // This method is called by the FXMLLoader when initialization is complete
    public void initialize(URL fxmlFileLocation, ResourceBundle resources) {
        assert measurePointIdComboBox != null : "fx:id=\"measurePointChoice\" was not injected: check your FXML file 'CustomMeasurePointFilter.fxml'.";
        assert measurePointText != null : "fx:id=\"measurePointText\" was not injected: check your FXML file 'CustomMeasurePointFilter.fxml'.";

        model.measurePointId.bind(measurePointText.textProperty());
       
        measurePointIdComboBox.getItems().clear();
        measurePointIdComboBox.getItems().addAll(copperDataProvider.getMonitoringMeasurePointIds());
        measurePointIdComboBox.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
				if (newValue!=null){
					measurePointText.setText(newValue);
				}
			}
		});
        measurePointIdComboBox.setStyle("-fx-text-overrun leading-ellipsis;");
        measurePointIdComboBox.setCellFactory(new Callback<ListView<String>, ListCell<String>>() {
			@Override
			public ListCell<String> call(ListView<String> param) {
				final TextFieldListCell<String> textFieldListCell = new TextFieldListCell<>();
				textFieldListCell.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
				return textFieldListCell;
			}
		});
	}

	@Override
	public CustomMeasurePointFilterModel getFilter() {
		return model;
	}

	@Override
	public URL getFxmlRessource() {
		return getClass().getResource("CustomMeasurePointFilter.fxml");
	}
	
	@Override
	public boolean supportsFiltering() {
		return true;
	}
	
	@Override
	public long getDefaultRefreshIntervall() {
		return 1500;
	}
	
}