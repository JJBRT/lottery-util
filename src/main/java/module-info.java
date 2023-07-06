module org.rg.game.lottery {

    requires jdk.unsupported;
	requires org.apache.poi.ooxml;
	requires com.fasterxml.jackson.databind;
	requires org.jsoup;
	requires org.apache.poi.poi;
	requires org.apache.xmlbeans;
	requires java.desktop;
	requires java.logging;
	requires javafx.graphics;
	requires com.formdev.flatlaf;

	opens org.rg.game.lottery.application to com.fasterxml.jackson.databind;

}
