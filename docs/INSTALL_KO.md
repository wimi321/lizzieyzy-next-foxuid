# 설치 가이드

이 문서는 `LizzieYzy Next-FoxUID` 를 최대한 빨리 설치해서 사용하고 싶은 사용자를 위한 안내입니다.

## 먼저 패키지를 고르세요

| 환경 | 추천 패키지 | Java | KataGo |
| --- | --- | --- | --- |
| Windows x64 | `<date>-windows64.with-katago.zip` | 포함 | 포함 |
| Windows x64 | `<date>-windows64.without.engine.zip` | 포함 | 없음 |
| Windows x86 | `<date>-windows32.without.engine.zip` | 없음 | 없음 |
| macOS Apple Silicon | `<date>-mac-arm64.with-katago.dmg` | App 내장 | 포함 |
| macOS Intel | `<date>-mac-amd64.with-katago.dmg` | App 내장 | 포함 |
| Linux x64 | `<date>-linux64.with-katago.zip` | 포함 | 포함 |

가장 쉽게 시작하려면 `with-katago` 패키지를 선택하세요.

## Windows

1. `windows64.with-katago.zip` 을 다운로드합니다.
2. 일반 폴더에 압축을 풉니다.
3. `start-windows64.bat` 를 실행합니다.
4. 첫 실행에서는 내장 KataGo 가 자동으로 감지되는 경우가 많아서 보통 수동 설정이 필요 없습니다.

## macOS

1. 칩에 맞는 dmg 를 선택합니다.
   - Apple Silicon: `mac-arm64.with-katago.dmg`
   - Intel: `mac-amd64.with-katago.dmg`
2. dmg 를 열고 앱을 `Applications` 로 드래그합니다.
3. `Applications` 에서 앱을 실행합니다.

현재 macOS 패키지는 서명 / 공증되지 않은 유지보수 빌드입니다. 처음 실행이 막히면:

1. 먼저 한 번 실행을 시도합니다.
2. `시스템 설정 -> 개인정보 보호 및 보안` 으로 이동합니다.
3. `그래도 열기` 를 선택합니다.
4. 다시 실행합니다.

## Linux

1. `linux64.with-katago.zip` 을 다운로드합니다.
2. 압축을 풉니다.
3. 터미널에서 실행합니다.

```bash
chmod +x start-linux64.sh
./start-linux64.sh
```

## Fox 기보 가져오기

앱을 실행한 뒤 Fox 기보 메뉴에서 **숫자만 있는 Fox ID** 를 입력하세요.

- 사용자명 검색은 더 이상 지원하지 않습니다.
- 최신 공개 기보를 가져오는 흐름으로 통일되어 있습니다.

## 관련 문서

- [Installation Guide (English)](INSTALL_EN.md)
- [Troubleshooting (English)](TROUBLESHOOTING_EN.md)
- [Package Overview (English)](PACKAGES_EN.md)
