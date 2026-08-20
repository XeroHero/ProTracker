module dev.xerohero.protracker {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.fasterxml.jackson.databind;


    opens dev.xerohero.protracker to javafx.fxml;
    exports dev.xerohero.protracker;
}