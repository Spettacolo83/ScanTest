package it.stefanorussello.scantest;

/**
 * Created by Spettacolo83 on 23/06/17.
 *
 * Class for storing every single item with its details.
 */

public class ItemData {

    public String title;
    public String brand;
    public String serialnumber;
    public String barcode;
    public String image;
    public String link;

    public ItemData(String title, String brand, String serialnumber, String barcode, String image, String link) {
        this.title = title;
        this.brand = brand;
        this.serialnumber = serialnumber;
        this.barcode = barcode;
        this.image = image;
        this.link = link;
    }
}
