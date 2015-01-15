package com.thuytrinh.squareplease;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainFragment extends Fragment {
  private static final int RC_PICK_PHOTO = 0;
  private final static String[] PHOTO_PROJECTION = new String[] {
      MediaStore.Images.Media._ID,
      MediaStore.Images.Media.BUCKET_ID,
      MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
      MediaStore.Images.Media.DATA
  };

  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View rootView = inflater.inflate(R.layout.fragment_main, container, false);

    View choosePhotoButton = rootView.findViewById(R.id.choosePhotoButton);
    choosePhotoButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        choosePhoto();
      }
    });

    return rootView;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode != Activity.RESULT_OK) {
      return;
    }

    Uri photoUri = data.getData();
    Cursor photoCursor = getActivity().getContentResolver().query(
        photoUri,
        PHOTO_PROJECTION,
        null, null, null
    );

    if (photoCursor.moveToFirst()) {
      String photoPath = photoCursor.getString(photoCursor.getColumnIndex(MediaStore.Images.Media.DATA));
      if (!TextUtils.isEmpty(photoPath)) {
        Bitmap originalPhoto = BitmapFactory.decodeFile(photoPath);
        int size = Math.max(originalPhoto.getWidth(), originalPhoto.getHeight());
        Bitmap squareBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(squareBitmap);
        canvas.drawColor(getResources().getColor(android.R.color.white));

        float x = Math.abs(squareBitmap.getWidth() - originalPhoto.getWidth()) * 0.5f;
        float y = Math.abs(squareBitmap.getHeight() - originalPhoto.getHeight()) * 0.5f;
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        canvas.drawBitmap(originalPhoto, x, y, paint);

        String name = String.format("square_pls_%d.png", System.currentTimeMillis());
        File squareBitmapFile = new File(getActivity().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name);
        try {
          FileOutputStream stream = new FileOutputStream(squareBitmapFile);
          squareBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
          stream.close();
        } catch (IOException e) {
          e.printStackTrace();
          return;
        }

        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(squareBitmapFile));
        shareIntent.setType("image/*");
        startActivity(shareIntent);
      }
    }
  }

  private void choosePhoto() {
    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
    intent.setType("image/*");
    startActivityForResult(intent, RC_PICK_PHOTO);
  }
}