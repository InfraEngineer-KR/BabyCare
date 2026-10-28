import numpy as np
import mediapipe as mp
from ultralytics import YOLO
from collections import Counter
import cv2
# faceLadnmarker에서 facedetector로 바꾸면서 추가한 것
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision

try:
    import config
except ImportError:
    class DummyConfig:
        YOLO_MODEL = "models/재학습522.pt"
        FACE_DETECTOR_MODEL = "models/blaze_face_short_range.tflite"
        VIDEO_BUFFER_SIZE = 10
    config = DummyConfig()


class VideoEngine:
    def __init__(self):
        # mediapipe 초기화

        base_options = mp_python.BaseOptions(
            model_asset_path=str(config.FACE_DETECTOR_MODEL)
        )
        det_options = vision.FaceDetectorOptions(
            base_options=base_options,
            min_detection_confidence=0.5,
        )
        self.face_detector = vision.FaceDetector.create_from_options(det_options)

        # YOLO 초기화
        self.yolo_model = YOLO(str(config.YOLO_MODEL))
        
        self.baby_yolo_model = YOLO("models/희수님모델.pt")

        print("✅ [VideoEngine] 모델 로드 완료")

        # 추후 매핑값으로 변경해 사용
        camera_points = np.float32([[0, 0], [1280, 0], [0, 720], [1280, 720]])
        thermal_points = np.float32([[40.441, -19.748], [-24.878, -12.697], [40.144, 22.022], [-25.175, 29.073]])
        self.H, _ = cv2.findHomography(camera_points, thermal_points)

    def analyze(self, batch_frames, thermal_frame):
        """
        MediaPipe Face Detector (키포인트 박스) + YOLO 분류
        """
        try:
            h, w, _ = batch_frames[0].shape
            img_area = h * w

            candidate_list = []   # [x1, y1, x2, y2, det_score]

            for frame in batch_frames:

                rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                mp_image = mp.Image(
                    image_format=mp.ImageFormat.SRGB,
                    data=rgb_frame
                )

                result = self.face_detector.detect(mp_image)
                if not result.detections:
                    continue

                for det in result.detections:
                    det_conf = det.categories[0].score
                    if det_conf < 0.5:
                        continue

                    # 키포인트 기준 박스 + 마진 20퍼 
                    # 디텍터는 얼굴 전체의 좌표를 찍는게 아니라 좀 작게 잡힘
                    xs = [kp.x * w for kp in det.keypoints]
                    ys = [kp.y * h for kp in det.keypoints]
                    x1, x2 = min(xs), max(xs)
                    y1, y2 = min(ys), max(ys)
                    mx, my = (x2 - x1) * 0.2, (y2 - y1) * 0.2

                    x1 = max(0, int(x1 - mx))
                    y1 = max(0, int(y1 - my))
                    x2 = min(w, int(x2 + mx))
                    y2 = min(h, int(y2 + my))

                    if x1 >= x2 or y1 >= y2:
                        continue

                    if ((x2 - x1) * (y2 - y1)) / img_area > 0.5:
                        continue

                    face_crop = frame[y1:y2, x1:x2]
                    if face_crop.size == 0:
                        continue

                    yolo_results = self.yolo_model(face_crop, verbose=False)
                    probs = yolo_results[0].probs
                    if probs is None:
                        continue

                    # 0=adult, 1=child, 2=infant, 3=senior
                    if int(probs.top1) != 2:
                        continue

                    candidate_list.append([x1, y1, x2, y2, det_conf])

            if not candidate_list:
                return {
                    "infant_detected": False,
                    "confidence": 0.0,
                    "bbox": None,
                    "thermal": None
                }

            # 검출 신뢰도 최고 박스 1개
            best = max(candidate_list, key=lambda b: b[4])
            x1, y1, x2, y2 = best[0], best[1], best[2], best[3]

            estimated_temp = None
            if thermal_frame is not None:
                pts = np.float32([[x1,y1], [x2,y1], [x1,y2], [x2,y2]]).reshape(-1,1,2)
                dst = cv2.perspectiveTransform(pts, self.H)

                # tx1, ty1 = int(dst[0][0][0]), int(dst[0][0][1])
                # tx2, ty2 = int(dst[3][0][0]), int(dst[3][0][1])
                xs, ys = dst[:, 0, 0], dst[:, 0, 1]
                tx1, tx2 = int(np.floor(xs.min())), int(np.ceil(xs.max()))
                ty1, ty2 = int(np.floor(ys.min())), int(np.ceil(ys.max()))

                th, tw = thermal_frame.shape[:2]
                tx1 = max(0, min(tx1, tw - 1))
                tx2 = max(0, min(tx2, tw))
                ty1 = max(0, min(ty1, th - 1))
                ty2 = max(0, min(ty2, th))

                if tx1 < tx2 and ty1 < ty2:
                    region = thermal_frame[ty1:ty2, tx1:tx2]
                    temps = region[(region >= 34.0) & (region <= 40.0)]
                    if len(temps) > 0:
                        estimated_temp = round(float(np.mean(temps)), 2)

            return {
                "infant_detected": True,
                "confidence": round(len(candidate_list) / len(batch_frames), 4),
                "bbox": [x1, y1, x2, y2],
                "thermal": estimated_temp
            }

        except Exception as e:
            print(f"❌ [VideoEngine] 분석 에러: {e}")
            return {"infant_detected": False, "error": str(e)}




    def analyze2(self, batch_frames, thermal_frame):
            """10장의 프레임으로 YOLO bbox 추출"""
            try:
                h, w, _ = batch_frames[0].shape
                
                bbox_list = []   # [x1, y1, x2, y2, conf]  # 수정: conf 추가 (best 1개 고르려고)
                
                for frame in batch_frames:
                    results = self.baby_yolo_model(frame, verbose=False)
                    
                    if len(results[0].boxes) == 0:
                        continue
                    
                    best_box = max(results[0].boxes, key=lambda b: float(b.conf[0].item()))
                    b = best_box.xyxy[0].tolist()
                    conf = float(best_box.conf[0].item())   # 추가: best 1개 선택용 conf
                    bbox_list.append([int(b[0]), int(b[1]), int(b[2]), int(b[3]), conf])   # 수정: conf 같이 저장
                
                if not bbox_list:
                    return {
                        "infant_detected": False,
                        "confidence": 0.0,
                        "bbox": None,
                        "thermal": None   # 추가: analyze와 반환 키 통일
                    }
                
                # 수정: 10장 union → 검출 신뢰도 최고 박스 1개
                # (union은 오탐 박스까지 끌어안아 거대 박스를 만들어 열화상 오염시킴)
                best = max(bbox_list, key=lambda b: b[4])
                x1, y1, x2, y2 = best[0], best[1], best[2], best[3]
                
                estimated_temp = None
                if thermal_frame is not None:
                    pts = np.float32([[x1,y1], [x2,y1], [x1,y2], [x2,y2]]).reshape(-1,1,2)
                    dst = cv2.perspectiveTransform(pts, self.H)
                    tx1, ty1 = int(dst[0][0][0]), int(dst[0][0][1])
                    tx2, ty2 = int(dst[3][0][0]), int(dst[3][0][1])

                    # 추가: thermal 범위 clamp (변환 후 좌표가 열화상 범위 밖 나가는 거 방지)
                    th, tw = thermal_frame.shape[:2]
                    tx1 = max(0, min(tx1, tw - 1))
                    tx2 = max(0, min(tx2, tw))
                    ty1 = max(0, min(ty1, th - 1))
                    ty2 = max(0, min(ty2, th))

                    if tx1 < tx2 and ty1 < ty2:   # 추가: 유효한 범위일 때만 계산
                        # 열화상 배열에서 해당 영역 추출
                        region = thermal_frame[ty1:ty2, tx1:tx2]
                        temps = region[(region >= 34.0) & (region <= 40.0)]
                        if len(temps) > 0:
                            estimated_temp = round(float(np.mean(temps)), 2)

                return {
                    "infant_detected": True,
                    "confidence": round(len(bbox_list) / len(batch_frames), 4),
                    "bbox": [x1, y1, x2, y2],
                    "thermal": estimated_temp
                }

            except Exception as e:
                print(f"❌ [VideoEngine] 분석 에러: {e}")
                return {"infant_detected": False, "error": str(e)}