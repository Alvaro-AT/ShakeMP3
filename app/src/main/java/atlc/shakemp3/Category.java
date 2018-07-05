package atlc.shakemp3;


import android.graphics.drawable.Drawable;

public class Category {
    //Atributos necesarios de las canciones
    private String categoryID;
    private String title;
    private String path;
    private Drawable icon;

    public Category() {
        super();
    }

    public Category(String categoryID, String title, String path, Drawable icon) {

        super();
        this.categoryID = categoryID;
        this.title = title;
        this.path = path;
        this.icon = icon;

    }

    public String getCategoryID() {
        return categoryID;
    }

    public String getTitle() {
        return title;
    }

    public String getPath(){ return path; }

    public Drawable getIcon() {
        return icon;
    }

    public void setCategoryID(String categoryID) {
        this.categoryID = categoryID;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setIcon(Drawable icon) {
        this.icon = icon;
    }

}
