# Clickify

<p align="center">
  <a href="README.md">English</a> | <b>Tiếng Việt</b> | <a href="README.zh-Hans.md">简体中文</a> | <a href="README.ja.md">日本語</a> | <a href="README.es.md">Español</a>
</p>

<p align="center">
  <img src="docs/assets/hero_showcase.jpg" alt="Trưng bày Clickify Đa Nền Tảng" width="100%" />
</p>

<p align="center">
  <a href="https://github.com/phanbaohuy96/clickify/actions"><img src="https://img.shields.io/badge/Nền_tảng-macOS%2014%2B%20%7C%20Android%2011%2B-000000?style=for-the-badge&logo=apple&logoColor=white" alt="Nền tảng" /></a>
  <a href="macos/"><img src="https://img.shields.io/badge/macOS-Swift%20%2F%20SwiftUI-F05138?style=for-the-badge&logo=swift&logoColor=white" alt="macOS Swift" /></a>
  <a href="android/"><img src="https://img.shields.io/badge/Android-Kotlin%20%2F%20Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Android Kotlin" /></a>
  <a href="docs/sdd/08-permissions-and-safety.md"><img src="https://img.shields.io/badge/An_toàn_chạm-SF--1%20Bắt_buộc-00C853?style=for-the-badge&logo=shield&logoColor=white" alt="An toàn chạm SF-1" /></a>
  <a href="docs/sdd/09-localisation.md"><img src="https://img.shields.io/badge/Ngôn_ngữ-EN%20%7C%20VI%20%7C%20ZH%20%7C%20JA%20%7C%20ES-blue?style=for-the-badge" alt="Ngôn ngữ" /></a>
</p>

Tự động hoá các tác vụ lặp đi lặp lại bằng cách phát các sự kiện nhập liệu giả lập: một chuỗi tuần tự các thao tác, mỗi thao tác là một **Hành động (Action)** đi kèm với một **Mục tiêu (Target)**, được ghi lại từ thao tác thực hoặc tạo thủ công và phát lại theo yêu cầu.

**Một sản phẩm, hai nền tảng.** Khái niệm thiết kế dùng chung; việc triển khai hoàn toàn native trên từng hệ điều hành.

| Nền tảng | Giao diện & Trạng thái | Mô tả |
|---|---|---|
| [**`macos/`**](macos/README.md) | **Menu-bar App** (macOS 14+) · *Đang phát hành* | Swift / SwiftUI, sử dụng ScreenCaptureKit và Apple Vision. Đã xác minh thủ công trên phần cứng thật — [bộ kiểm thử e2e thủ công, ghi lại từng kết quả một](docs/manual-e2e-tests.md). |
| [**`android/`**](android/README.md) | **Floating Overlay Service** (Android 11+) · *Đã hoàn thiện 4 lát cắt* | Lớp phủ Jetpack Compose điều khiển qua AccessibilityService. Đã chạy trên máy ảo — **chưa chạy trên thiết bị vật lý** ([kế hoạch kiểm thử](android/docs/testing.md)). |

---

## Tại sao chọn Clickify?

Đa số các ứng dụng auto-click hiện nay trên thị trường hoặc là **đồ chơi nhiều lỗi** (hơn 100 triệu lượt tải nhưng dính lỗi đơ cảm ứng kinh điển phải khởi động lại máy) hoặc là **công cụ quá phức tạp** (bắt học cả ngôn ngữ kịch bản chỉ để bấm 1 nút). Clickify đứng ở khoảng trống tiềm năng ở giữa. Bằng chứng thực tế được phân tích chi tiết trong tài liệu khảo sát thị trường [`android/docs/landscape.md`](android/docs/landscape.md).

```
                      ┌──────────────────────────────────────────────┐
                      │                 Clickify                   │
                      │  UI Hiện đại · Nhận diện ảnh · Chống crash   │
                      │   An toàn không đơ chạm · Thiết kế chuẩn mực │
                      └──────────────────────┬───────────────────────┘
                                             │
               ┌─────────────────────────────┴─────────────────────────────┐
               ▼                                                           ▼
┌──────────────────────────────┐                           ┌──────────────────────────────┐
│  Clicker cơ bản (100M+ tải)  │                           │   Công cụ kịch bản phức tạp  │
│  Lỗi đơ cảm ứng phải reboot  │                           │   Độ dốc học tập quá cao     │
│  Không nhận diện ảnh/chữ     │                           │   Mã code/token khó hiểu     │
│  Thuê bao tuần đắt đỏ        │                           │   Gây nóng máy và hao pin    │
└──────────────────────────────┘                           └──────────────────────────────┘
```

### 1. 🛡️ An toàn cảm ứng không đơ máy (`SF-1` và "Giải phóng chạm")

Lỗi tồi tệ nhất trong toàn bộ nhóm ứng dụng này là **đơ cảm ứng do chạm đồng thời (stuck-touch freeze)**: chạm tay vào màn hình đúng thời điểm clicker phát sự kiện giả lập, hệ thống giữ chặt điểm chạm cuối và điện thoại ngừng nhận mọi thao tác của ngón tay cho đến khi khởi động lại máy. [Báo cáo trên diễn đàn XDA cho thấy lỗi này tái hiện trên *tất cả* các app nổi tiếng được thử nghiệm](android/docs/landscape.md).

- **`SF-1`** — Bộ điều phối luôn giải phóng mọi phím chuột đang giữ và mọi nét chạm cảm ứng trên **bất kỳ** lối thoát nào: hoàn thành, bấm Dừng, huỷ bỏ, hoặc gặp lỗi ([tài liệu an toàn](docs/sdd/08-permissions-and-safety.md)).
- Trên Android, tính năng **Giải phóng chạm (Free the touch)** cho phép khắc phục chỉ bằng một chạm từ thông báo thường trực hoặc ô Cài đặt nhanh (Quick Settings tile), nhả điểm chạm bị kẹt mà không cần khởi động lại điện thoại.

<p align="center">
  <img src="docs/assets/touch-safety.jpg" alt="Điểm chạm bị kẹt ở bên trái, đã được nhả ở bên phải" width="78%" />
</p>
<p align="center"><em>Trái: điểm chạm bị kẹt — lỗi mà cả nhóm ứng dụng này đều dính. Phải: đã nhả — <code>SF-1</code> nhả ở mọi lối thoát, còn Giải phóng chạm là để cứu điểm đã kẹt sẵn.</em></p>

### 2. 👁️ Định vị mục tiêu thông minh thay vì tin vào toạ độ mù

Toạ độ cố định sẽ hỏng ngay khi cửa sổ di chuyển, banner quảng cáo trượt, hoặc giao diện thay đổi bố cục.

- **Khớp mẫu ảnh (Template matching)** — Cắt một vùng ảnh trực tiếp từ màn hình và gán **Bước** hướng vào đó. Trên macOS, hệ thống khớp ở hai tỷ lệ để **Mẫu** vẫn nhận diện được khi chuyển đổi giữa màn hình Retina và màn hình phụ ngoài ([ADR-0008](docs/adr/0008-match-templates-at-two-scales.md)). Android khớp ở một tỷ lệ cố định vì **Kịch bản** gắn chặt với màn hình tạo ra nó ([ADR-0013](android/docs/adr/0013-coordinates-are-raw-pixels-bound-to-a-screen-profile.md)).
- **Nhận diện chữ (OCR)** — macOS nhận diện theo từ ngữ (*"Lưu"*, *"Nhận thưởng"*, *"Gửi"*) thông qua Apple Vision. **Chưa hỗ trợ trên Android** do phụ thuộc ML Kit và app không phân phối qua Google Play ([10-recognition.md](android/docs/sdd/10-recognition.md)).
- **Cơ chế xử lý khi hết giờ rõ ràng (`DM-16`)** — Mọi lượt tìm kiếm đều có thời gian chờ tối đa bạn tự đặt, và bạn chọn điều gì sẽ xảy ra khi hết giờ: bỏ qua **Bước** đó, hoặc dừng toàn bộ **Kịch bản**. Tuyệt đối không treo ngầm ứng dụng.

<p align="center">
  <img src="docs/assets/android-crop.png" alt="Cắt mẫu trực tiếp từ màn hình trên Android" width="31%" />
  &nbsp;
  <img src="docs/assets/android-step-find.png" alt="Bước được tạo ra với Mẫu ảnh, ngưỡng tin cậy, thời gian chờ và hành vi khi hết giờ" width="31%" />
</p>
<p align="center"><em>Android trên máy ảo: Cắt vùng ảnh cần tìm, sau đó cấu hình Bước tương ứng.</em></p>

### 3. 🧩 Mô hình Trực giao Hành động × Mục tiêu

Một **Bước** ghép nối chính xác một **Hành động** với một **Mục tiêu** ([ADR-0002](docs/adr/0002-step-is-action-times-target.md)):

- **Hành động (Actions)** — macOS: click (đơn, đúp, ba lần, giữ), cuộn, di chuyển chuột, kéo, gõ chuỗi ký tự, phím tắt. Android: chạm (tap), vuốt (swipe), đa chạm (multi-touch), hành động hệ thống (global action), nhập văn bản (set text).
- **Mục tiêu (Targets)** — macOS: tại con trỏ, toạ độ tuyệt đối, toạ độ tương đối theo góc cửa sổ gần nhất, **Mẫu** ảnh, đoạn văn bản OCR. Android: một toạ độ, có thể di dời theo kết quả tìm kiếm **Mẫu** ảnh.
- **Không rẽ nhánh rối rắm** — Một **Bước** tự quyết định kết quả của chính nó khi hết giờ và không bao giờ can thiệp vào Bước khác ([ADR-0011](docs/adr/0011-a-scenario-has-no-branches.md)). Không có logic `if`/`else` phức tạp gây lỗi.

<p align="center">
  <img src="docs/assets/action-times-target.jpg" alt="Mọi Hành động đều ghép được với mọi Mục tiêu" width="78%" />
</p>
<p align="center"><em>Hành động bên trái, Mục tiêu bên phải — một Bước là một cặp, và hai bên được chọn độc lập với nhau.</em></p>

### 4. 📱 Lớp phủ Nổi trên Android (Floating Overlay)

- Bảng điều khiển nổi có thể di chuyển tuỳ ý, không che khuất ứng dụng bạn đang tự động hoá.
- Các điểm đánh dấu (**Marker**) có số thứ tự, kéo thả trực tiếp đến vị trí mong muốn trên màn hình.
- Thao tác vuốt với thời lượng tuỳ chỉnh và cử chỉ đa chạm tự định cấu hình.
- 5 ngôn ngữ giao diện chuyển đổi **ngay tức thì**, áp dụng đồng thời cho cả Activity và lớp phủ mà không cần khởi động lại ([11-localisation.md](android/docs/sdd/11-localisation.md)).

<p align="center">
  <img src="docs/assets/android-languages.png" alt="Danh sách kịch bản phía sau và bảng điều khiển phía trước đều đổi sang tiếng Việt" width="31%" />
</p>
<p align="center"><em>Một lựa chọn, áp dụng cả hai bề mặt, không cần khởi động lại.</em></p>

### 5. 🎥 Ghi lại chính xác thao tác thực tế

- **macOS** — Bấm `⌥⌘R` để bắt đầu và kết thúc một phiên ghi. Hệ thống giữ nguyên nhịp điệu thời gian thực của bạn thay vì làm phẳng thành khoảng thời gian cố định ([ADR-0004](docs/adr/0004-recordings-keep-real-timing.md)). Tự động chuyển sang toạ độ tương đối với cửa sổ nếu phiên ghi diễn ra trọn vẹn trong một ứng dụng.
- **Android** — Lớp ghi nhận từng nét chạm, lưu lại và truyền lại cho ứng dụng bên dưới để bạn ghi hình bằng cách thao tác bình thường ([08-recording.md](android/docs/sdd/08-recording.md)).
- **Chỉ theo dõi chuột và nét chạm, tuyệt đối không theo dõi bàn phím.** Đây là quyết định có chủ đích: theo dõi phím bấm sẽ biến app thành phần mềm độc hại ghi lén phím (keylogger) ([ADR-0003](docs/adr/0003-no-keyboard-capture-when-recording.md)).

---

## Bảng so sánh thị trường

Tổng hợp từ khảo sát thực tế trong [`android/docs/landscape.md`](android/docs/landscape.md):

| Tiêu chí | Auto-clicker truyền thống *(True Developers,...)* | Macrorify | Klick'r / Smart AutoClicker | **Clickify** |
|---|---|---|---|---|
| **Khắc phục đơ chạm** | ❌ Lỗi toàn ngành; cách duy nhất là reboot | ⚠️ Khắc phục một phần | ⚠️ Chưa xử lý bài bản | ✅ **`SF-1` + Nút "Giải phóng chạm" 1 chạm** |
| **Nhận diện hình ảnh** | ❌ Không có | ✅ Mẫu ảnh + OCR | ✅ Kích hoạt theo ảnh | ✅ **Mẫu ảnh trên cả 2 nền tảng; OCR trên macOS** |
| **Độ khó sử dụng** | Thấp, vì quá ít tính năng | Rất cao — logic phức tạp hoặc mã script | Cao; bị đánh giá là điểm trừ lớn nhất | **Dễ đến Trung bình, trực quan** |
| **Đa nền tảng** | ❌ Chỉ Android | ❌ Chỉ Android | ❌ Chỉ Android | ✅ **Cả macOS và Android, đều là native** |
| **Quy chuẩn kỹ thuật** | — | — | — | ✅ **Mọi hành vi đều là yêu cầu được đánh số trong mã nguồn** |

---

## Khả năng của từng nền tảng

| Khả năng | macOS (`macos/`) | Android (`android/`) |
|---|:---:|:---:|
| **Môi trường chạy** | Menu-bar popover + ScreenCaptureKit | Dịch vụ Foreground + Lớp phủ AccessibilityService |
| **Hành động** | Click, cuộn, di chuyển, kéo, gõ chữ, phím tắt | Chạm, vuốt, đa chạm, thao tác hệ thống, đặt văn bản |
| **Mục tiêu: con trỏ / điểm cố định** | ✅ Cả hai | ✅ Điểm cố định đặt bằng **Marker** |
| **Mục tiêu: khoảng cách tương đối cửa sổ** | ✅ Bám theo góc gần nhất | N/A — không có khái niệm cửa sổ |
| **Mục tiêu: Mẫu ảnh (Template)** | ✅ Bộ khớp đa tỷ lệ | ✅ Khớp một tỷ lệ ([ADR-0013](android/docs/adr/0013-coordinates-are-raw-pixels-bound-to-a-screen-profile.md)) |
| **Mục tiêu: Chữ (OCR)** | ✅ Apple Vision | ❌ Chưa có — phụ thuộc ML Kit và không phát hành qua Play |
| **Ghi thao tác** | ✅ Sự kiện chuột, giữ thời gian thực | ✅ Chạm xuyên qua; chưa ghi cử chỉ đa chạm |
| **Ngôn ngữ giao diện** | ✅ 5 ngôn ngữ, đổi tức thì | ✅ 5 ngôn ngữ, đổi tức thì trên cả 2 bề mặt |
| **Xác minh trên phần cứng thật** | ✅ [bộ kiểm thử e2e thủ công](docs/manual-e2e-tests.md) | ❌ Mới kiểm thử trên máy ảo ([testing](android/docs/testing.md)) |

---

## Định hướng phát triển

**Các tính năng này đang trong kế hoạch.** Được ghi nhận tại đây để làm rõ định hướng sản phẩm:

- **Tính năng cốt lõi luôn miễn phí.** Click không giới hạn, cử chỉ đa điểm, ghi thao tác và an toàn chạm `SF-1`.
- **Nâng cấp một lần thay vì thuê bao định kỳ**, điều mà người dùng đánh giá cao nhất ở nhóm ứng dụng này ([landscape](android/docs/landscape.md)).
- **Các tính năng trả phí tiềm năng**: Chống phát hiện bot (độ lệch ngẫu nhiên Gauss), đường vuốt cong Bézier tự nhiên, hẹn giờ theo đồng hồ thực tế, cảm biến mã màu (Color Guard) và không giới hạn slot kịch bản.

---

## Hướng dẫn cài đặt nhanh

### macOS (macOS 14 Sonoma trở lên)

Yêu cầu Xcode Command Line Tools.

```bash
git clone https://github.com/phanbaohuy96/clickify.git
cd clickify/macos

swift test              # Chạy bộ test đơn vị và test đặc tả
./scripts/install.sh    # Build và cài đặt vào /Applications
```

> **Lần đầu chạy**: Cấp quyền **Trợ năng (Accessibility)** trong `Cài đặt hệ thống → Quyền riêng tư & Bảo mật → Trợ năng`, và **Ghi màn hình (Screen Recording)** nếu dùng tính năng tìm ảnh hoặc chữ.
> **Dừng khẩn cấp**: Phím tắt **`⌥⌘S`** lập tức dừng mọi thao tác và nhả toàn bộ phím/chuột tại bất kỳ thời điểm nào.

### Android (Android 11 trở lên)

```bash
cd clickify/android

./gradlew assembleDebug        # Build file APK debug
./gradlew testDebugUnitTest    # Chạy unit tests
```

> **Lần đầu chạy**: Ứng dụng sẽ hướng dẫn cấp quyền vẽ trên ứng dụng khác (overlay) và quyền trợ năng. Trên Android 13 trở lên, dịch vụ bị hạn chế theo mặc định cho đến khi bạn cấp phép thủ công trong Cài đặt ứng dụng — xem [`02-permissions-and-onboarding.md`](android/docs/sdd/02-permissions-and-onboarding.md).

---

## Tài liệu & Quy chuẩn kỹ thuật

Đặc tả kỹ thuật luôn đi **trước** mã nguồn trên cả hai nền tảng. Mọi hành vi quan sát được đều là yêu cầu có số hiệu trong tài liệu `sdd/`, được trích dẫn trực tiếp trong code.

| Tài liệu | Giải đáp |
|---|---|
| [`CONTEXT-MAP.md`](CONTEXT-MAP.md) | Bảng thuật ngữ tương ứng cho từng nền tảng |
| [`CONTEXT.md`](CONTEXT.md) | Ngôn ngữ miền chung — tên gọi và ý nghĩa của các khái niệm |
| [`docs/adr/`](docs/adr/) | Lý do đưa ra các quyết định kỹ thuật — `0001`–`0011` chung/macOS, `0012`+ Android |
| [`docs/sdd/`](docs/sdd/) | Mục tiêu sản phẩm và thông số kỹ thuật macOS |
| [`android/docs/`](android/docs/) | Thuật ngữ, đặc tả, quyết định, khảo sát thị trường và kế hoạch test Android |
| [`docs/manual-e2e-tests.md`](docs/manual-e2e-tests.md) | Những bài kiểm tra thực tế trên phần cứng thật và kết quả đo đạc |

---

## Ngôn ngữ giao diện

Hỗ trợ 5 ngôn ngữ trên cả hai nền tảng, đổi tức thì không cần khởi động lại app:

🇬🇧 **English** · 🇻🇳 **Tiếng Việt** · 🇨🇳 **中文（简体）** · 🇯🇵 **日本語** · 🇪🇸 **Español**

---

## Giấy phép bản quyền

Dự án được phân phối dưới giấy phép [MIT](LICENSE).
