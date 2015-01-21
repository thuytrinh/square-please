package com.thuytrinh.squareplease;

import android.app.Activity;
import android.content.Context;
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
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

public class MainFragment extends Fragment {
  private static final int RC_PICK_PHOTO = 0;
  private final static String[] PHOTO_PROJECTION = new String[] {
      MediaStore.Images.Media._ID,
      MediaStore.Images.Media.BUCKET_ID,
      MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
      MediaStore.Images.Media.DATA
  };

  private View choosePhotoButton;
  private View progressBar;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setRetainInstance(true);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View rootView = inflater.inflate(R.layout.fragment_main, container, false);

    choosePhotoButton = rootView.findViewById(R.id.choosePhotoButton);
    choosePhotoButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        choosePhoto();
      }
    });

    progressBar = rootView.findViewById(R.id.progressBar);

    return rootView;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode != Activity.RESULT_OK) {
      return;
    }

    progressBar.setVisibility(View.VISIBLE);
    choosePhotoButton.setVisibility(View.GONE);

    final Uri photoUri = data.getData();
    final Context appContext = getActivity().getApplicationContext();
    Observable
        .create(new Observable.OnSubscribe<Uri>() {
          @Override
          public void call(Subscriber<? super Uri> subscriber) {
            Cursor photoCursor = getActivity().getContentResolver().query(
                photoUri,
                PHOTO_PROJECTION,
                null, null, null
            );

            if (!photoCursor.moveToFirst()) {
              subscriber.onError(new FileNotFoundException());
              return;
            }

            String photoPath = photoCursor.getString(photoCursor.getColumnIndex(MediaStore.Images.Media.DATA));
            if (TextUtils.isEmpty(photoPath)) {
              subscriber.onError(new FileNotFoundException());
              return;
            }

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
              subscriber.onError(e);
              return;
            }

            subscriber.onNext(Uri.fromFile(squareBitmapFile));
          }
        })
        .subscribeOn(Schedulers.newThread())
        .observeOn(AndroidSchedulers.mainThread())
        .finallyDo(new Action0() {
          @Override
          public void call() {
            progressBar.setVisibility(View.GONE);
            choosePhotoButton.setVisibility(View.VISIBLE);
          }
        })
        .subscribe(new Action1<Uri>() {
          @Override
          public void call(Uri squarePhotoUri) {
            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_STREAM, squarePhotoUri);
            shareIntent.setType("image/*");
            startActivity(shareIntent);
          }
        }, new Action1<Throwable>() {
          @Override
          public void call(Throwable throwable) {
            if (throwable instanceof FileNotFoundException) {
              Toast.makeText(appContext, "Failed to load image", Toast.LENGTH_SHORT).show();
            } else {
              Toast.makeText(appContext, "Unexpected error", Toast.LENGTH_SHORT).show();
            }
          }
        });
  }

  private void choosePhoto() {
    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
    intent.setType("image/*");
    startActivityForResult(intent, RC_PICK_PHOTO);
  }
}