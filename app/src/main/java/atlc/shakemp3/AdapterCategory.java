package atlc.shakemp3;


import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

//Adapter para la listView que mostrará la lista de canciones
public class AdapterCategory extends BaseAdapter {

    protected Activity activity;
    //ArrayList de una estructura de datos que contendrá los
    //atributos principales (los que necesitaremos) de cada canción.
    protected ArrayList<Category> categories;

    public AdapterCategory(Activity activity, ArrayList<Category> categories) {

        this.activity = activity;
        this.categories = categories;

    }

    public Object getItem(int id) {
        return categories.get(id);
    }

    public long getItemId(int position) {
        return position;
    }

    public int getCount() {
        return categories.size();
    }

    public View getView(int position, View convertView, ViewGroup parent) {

        View v = convertView;

        if (convertView == null) {
            //Aquí cogemos nuestra vista personalizada para la listView
            LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.item_category, null);
        }

        Category cat = categories.get(position);

        TextView title = (TextView) v.findViewById(R.id.textView);
        title.setText(cat.getTitle());

        ImageView icon = (ImageView) v.findViewById(R.id.imageView);
        icon.setImageDrawable(cat.getIcon());

        return v;
    }

}
