# FFmpeg Commands Cheat Sheet

Comprehensive FFmpeg commands for media processing.

## 1. General Information
- `ffmpeg -version`: Check version and build details.
- `ffmpeg -formats`: List supported formats.
- `ffmpeg -codecs`: List supported codecs.
- `ffmpeg -filters`: List available filters.

## 2. Inspecting Media Files (ffprobe)
- **Basic file info:** `ffmpeg -i input.mp4`
- **Detailed stream info:**
  ```bash
  ffprobe -v error -show_format -show_streams input.mp4
  ```
- **Get resolution:**
  ```bash
  ffprobe -v error -select_streams v:0 -show_entries stream=width,height -of csv=s=x:p=0 input.mp4
  ```
- **Get duration:**
  ```bash
  ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 input.mp4
  ```

## 3. Basic Conversions
- **Convert format:** `ffmpeg -i input.mp4 output.mkv`
- **Extract audio:** `ffmpeg -i input.mp4 -vn -c:a copy output.mp3`
- **Remove audio:** `ffmpeg -i input.mp4 -an -c:v copy output.mp4`

## 4. Video Processing
- **Change resolution:**
  ```bash
  ffmpeg -i input.mp4 -vf scale=1280:720 output.mp4
  ```
- **Change aspect ratio:**
  ```bash
  ffmpeg -i input.mp4 -aspect 16:9 output.mp4
  ```
- **Rotate video:**
  ```bash
  ffmpeg -i input.mp4 -vf "transpose=1" output.mp4
  ```
- **Crop video:**
  ```bash
  ffmpeg -i input.mp4 -vf "crop=w:h:x:y" output.mp4
  ```
  *(e.g., crop=640:480:0:0 for top-left)*

## 5. Trimming and Cutting
- **Trim from start time (duration):**
  ```bash
  ffmpeg -i input.mp4 -ss 00:00:10 -t 00:00:30 -c copy output.mp4
  ```
- **Trim from start to end time:**
  ```bash
  ffmpeg -i input.mp4 -ss 00:00:10 -to 00:00:40 -c copy output.mp4
  ```

## 6. Video Encoding & Quality
- **H.264 Encoding (CRF for quality):**
  ```bash
  ffmpeg -i input.mp4 -c:v libx264 -crf 23 -preset medium output.mp4
  ```
  *(CRF 0-51: 0 is lossless, 23 is default, 51 is worst quality. Presets: ultrafast, fast, medium, slow, veryslow)*
- **H.265 (HEVC) Encoding:**
  ```bash
  ffmpeg -i input.mp4 -c:v libx265 -crf 28 output.mp4
  ```

## 7. Audio Processing
- **Change bitrate:** `ffmpeg -i input.mp3 -ab 192k output.mp3`
- **Change volume:** `ffmpeg -i input.mp3 -af "volume=1.5" output.mp3`
- **Mix audio and video:**
  ```bash
  ffmpeg -i video.mp4 -i audio.mp3 -c:v copy -c:a aac -map 0:v:0 -map 1:a:0 output.mp4
  ```

## 8. Image Sequences
- **Extract all frames:** `ffmpeg -i input.mp4 thumb%04d.jpg`
- **Extract one frame at 10s:** `ffmpeg -ss 00:00:10 -i input.mp4 -frames:v 1 output.jpg`
- **Create video from images:**
  ```bash
  ffmpeg -framerate 24 -i img%03d.jpg output.mp4
  ```

## 9. Advanced Filters
- **Create GIF:**
  ```bash
  ffmpeg -i input.mp4 -vf "fps=10,scale=320:-1:flags=lanczos" output.gif
  ```
- **Add text overlay:**
  ```bash
  ffmpeg -i input.mp4 -vf "drawtext=text='Hello World':fontcolor=white:fontsize=24:box=1:boxcolor=black@0.5:boxborderw=5:x=(w-text_w)/2:y=(h-text_h)/2" output.mp4
  ```
- **Adjust playback speed:**
  - Double speed (Video): `ffmpeg -i input.mp4 -vf "setpts=0.5*PTS" output.mp4`
  - Double speed (Audio): `ffmpeg -i input.mp4 -af "atempo=2.0" output.mp4`

## 10. Concatenation
- **Using demuxer (files of same format):**
  Create `files.txt`:
  ```text
  file 'input1.mp4'
  file 'input2.mp4'
  ```
  Then run:
  ```bash
  ffmpeg -f concat -safe 0 -i files.txt -c copy output.mp4
  ```

## 11. Streaming and Thumbnails
- **Generate thumbnail every 60 seconds:**
  ```bash
  ffmpeg -i input.mp4 -f image2 -bt 20M -vf fps=1/60 thumbs%03d.jpg
  ```
- **Generate a seekable thumbnail (fast):**
  ```bash
  ffmpeg -ss 00:00:10 -i input.mp4 -vframes 1 -q:v 2 output.jpg
  ```
