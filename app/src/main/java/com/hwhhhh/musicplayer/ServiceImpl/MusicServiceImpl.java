package com.hwhhhh.musicplayer.ServiceImpl;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.MediaPlayer;
import android.util.Log;

import com.hwhhhh.musicplayer.MainActivity;
import com.hwhhhh.musicplayer.Service.MusicChangedListener;
import com.hwhhhh.musicplayer.Service.MusicPlayingChangedListener;
import com.hwhhhh.musicplayer.Service.MusicService;
import com.hwhhhh.musicplayer.Service.SongSheetService;
import com.hwhhhh.musicplayer.entity.SongBean;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

public class MusicServiceImpl implements MusicService {
    private static final String TAG = "MusicServiceImpl";
    private static MusicServiceImpl musicServiceImpl = null;

    private MediaPlayer mediaPlayer;
    private String[] musicNames, randomNames;
    private int currentPosition, currentIndex, order = PLAY_ORDER;
    private AssetManager assetManager;//资源管理器
    private String currentMusicName;
    private MusicChangedListener musicChangedListener;      //当歌曲发生变换
    private MusicPlayingChangedListener musicPlayingChangedListener;    //当歌曲播放状态变换

    private MusicServiceImpl(Context context) {
        mediaPlayer = new MediaPlayer();
        assetManager = context.getAssets();
        try {
            musicNames = assetManager.list("music");    //获取assets/music下所有文件
            if (musicNames != null) {
                randomNames = new String[musicNames.length];//生成随机播放数组
               // SongSheetService songSheetService = new SongSheetServiceImpl();
                //深拷贝
                for (int i = 0; i < musicNames.length; i++) {
                    randomNames[i] = musicNames[i];
//运行一次即可，用于将本地音乐存入SongBean
//                    SongBean songBean = new SongBean(musicNames[i], songSheetService.findAll().get(0).getId());
//                    if (songBean.save()) {
//                        Log.d(TAG, "MusicServiceImpl: save " + musicNames[i] + "successfully!");
//                    } else {
//                        Log.d(TAG, "MusicServiceImpl: default!");
//                    }
                }
                currentMusicName = musicNames[0];
                loadMusic(currentMusicName);//默认加载第一首歌
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static MusicService getInstance(Context context) {
        if (musicServiceImpl == null) {
            synchronized (MusicServiceImpl.class) {
                if (musicServiceImpl == null) {
                    musicServiceImpl = new MusicServiceImpl(context);
                }
            }
        }
        return musicServiceImpl;
    }



    @Override
    public void loadMusic(String musicName){
        currentMusicName = musicName;
        try {
            //重置
            mediaPlayer.reset();//reset后的mediaPlayer进入Idle状态。需要重新设置音源与准备
            AssetFileDescriptor afd = assetManager.openFd("music/" + currentMusicName);//读取当前歌曲的资源文件描述符
            mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());//设置dataSource
            mediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void play(String musicName) {
        if (musicName == null) {    //对当前的歌曲进行操作
            if (mediaPlayer.isPlaying()) {  //如果在播放就暂停
                onPause();
            } else {    //当暂停时
                if (currentPosition > 0) {  //如果当前播放位置大于0，就继续播放
                    onResume();
                } else {    //为0开始播放歌曲
                    start();
                }
            }
        } else if (!currentMusicName.equals(musicName)){    //传进的歌曲名称不等当前歌曲，表示换歌，播放musicName
            loadMusic(musicName);
            this.musicChangedListener.refresh();
            start();
        } else {    //当相等时，即重复点击
            if (!mediaPlayer.isPlaying()) {     //如果暂停则播放，播放则不操作
                start();
            }
        }
    }

    private void start() {
        mediaPlayer.start();
        this.musicPlayingChangedListener.afterChanged();    //监听歌曲，此时发生变换
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                next();
            }
        });
    }

    @Override
    public void onPause() {
        if (mediaPlayer.isPlaying()) {
            currentPosition = mediaPlayer.getCurrentPosition();     //获取当前播放位置
            mediaPlayer.pause();
            this.musicPlayingChangedListener.afterChanged();
        }
    }

    @Override
    public void seekTo(int progress) {
        if (!mediaPlayer.isPlaying()){
            play(null);
        }
        mediaPlayer.seekTo(progress);
    }

    @Override
    public void onResume() {
        if (!mediaPlayer.isPlaying()){
            mediaPlayer.start();
            this.musicPlayingChangedListener.afterChanged();
            mediaPlayer.seekTo(currentPosition);
            currentPosition = 0;
        }
    }

    @Override
    public void setPlayOrder(int i) {
        if (i == PLAY_ORDER) {
            order = PLAY_ORDER;
            currentIndex = Arrays.binarySearch(musicNames, currentMusicName);     //二分法查找当前播放音乐的索引
            Log.d(TAG, "setPlayOrder: currentIndex order " + currentIndex);
        } else {
            order = PLAY_RANDOM;
            shuffleCard(musicNames);
            currentIndex = search(randomNames, currentMusicName);    //二分法查找当前播放音乐的索引
            Log.d(TAG, "setPlayOrder: currentIndex random " + currentIndex);
        }
    }

    @Override
    public void next() {
        if (order == PLAY_ORDER) {  //顺序播放
            if (currentIndex < musicNames.length - 1) {
                play(musicNames[++currentIndex]);
            } else {
                currentIndex = 0;
                play(musicNames[currentIndex]);
            }
        } else {    //随机播放
            if (currentIndex < randomNames.length - 1) {
                play(randomNames[++currentIndex]);
            } else {
                currentIndex = 0;
                play(randomNames[currentIndex]);
            }
        }
        this.musicChangedListener.refresh();
    }

    @Override
    public void last() {
        if (order == PLAY_ORDER) {  //顺序播放
            if (currentIndex > 0) {
                play(musicNames[--currentIndex]);
            } else {
                currentIndex = musicNames.length - 1;
                play(musicNames[currentIndex]);
            }
        } else {    //随机播放
            if (currentIndex > 0) {
                play(randomNames[--currentIndex]);
            } else {
                currentIndex = randomNames.length - 1;
                play(randomNames[currentIndex]);
            }
        }
        this.musicChangedListener.refresh();
    }

    @Override
    public int getCurrentProgress() {
        if (mediaPlayer != null) {
            return mediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    @Override
    public void onDestroy() {
        assetManager.close();
        mediaPlayer.release();
    }

    /**
     * 洗牌算法
     * @param names 顺序播放的musicNames
     */
    private void shuffleCard(String[] names) {
        int len = names.length;
        Random r = new Random();
        for (int i = 0; i < len; i++) {
            int index = r.nextInt(len); //向下取整 如[0, 5）则index为0， 1， 2， 3， 4
            String temp = randomNames[i];
            randomNames[i] = randomNames[index];
            randomNames[index] = temp;
        }
        for (int i = 0; i < len; i++) {
            Log.d(TAG, "shuffleCard: random " + randomNames[i]);
        }
    }

    @Override
    public int getPlayOrder() {
        return order;
    }

    @Override
    public String getCurrentMusicInfo() {
        String str = currentMusicName.substring(0, currentMusicName.length()-4);
        String[] info = str.split(" - ");
        return info[1] + "\n" + info[0];
    }

    @Override
    public int getDuration() {
        return mediaPlayer.getDuration();
    }

    @Override
    public String[] getMusicNames() {
        return musicNames;
    }

    @Override
    public boolean isPlaying() {
        return mediaPlayer.isPlaying();
    }

    @Override
    public void setMusicChangedListener(MusicChangedListener musicChangedListener) {
        this.musicChangedListener = musicChangedListener;
    }

    private int search(String[] randomNames, String a) {
        for (int i = 0; i < randomNames.length; i ++) {
            if (a.equals(randomNames[i])) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public void setMusicPlayingChangedListener(MusicPlayingChangedListener musicPlayingChangedListener) {
        this.musicPlayingChangedListener = musicPlayingChangedListener;
    }

}
