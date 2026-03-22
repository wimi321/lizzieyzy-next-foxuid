<p align="center">
  <img src="assets/hero.svg" alt="LizzieYzy Next-FoxUID" width="100%" />
</p>

<p align="center">
  <a href="https://github.com/wimi321/lizzieyzy-next-foxuid/releases"><img src="https://img.shields.io/github/v/release/wimi321/lizzieyzy-next-foxuid?display_name=tag&label=Release&color=1B4D3E" alt="Release"></a>
  <a href="https://github.com/wimi321/lizzieyzy-next-foxuid/actions/workflows/ci.yml"><img src="https://github.com/wimi321/lizzieyzy-next-foxuid/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://github.com/wimi321/lizzieyzy-next-foxuid/stargazers"><img src="https://img.shields.io/github/stars/wimi321/lizzieyzy-next-foxuid?style=flat&color=7F4F24" alt="Stars"></a>
  <a href="https://github.com/wimi321/lizzieyzy-next-foxuid/releases"><img src="https://img.shields.io/github/downloads/wimi321/lizzieyzy-next-foxuid/total?label=Downloads&color=2F4858" alt="Downloads"></a>
  <a href="LICENSE.txt"><img src="https://img.shields.io/badge/License-GPL%20v3-E7A23B" alt="License"></a>
</p>

<p align="center">
  <a href="README.md">中文</a> · <a href="README_EN.md">English</a> · <a href="README_JA.md">日本語</a> · 한국어
</p>

<p align="center">
  <strong>망가진 Fox 기보 동기화를 다시 쓸 수 있게 되돌리는 유지보수판입니다.</strong><br/>
  이 포크의 목적은 내려받아 바로 열고, <strong>숫자 Fox ID</strong> 를 입력해 기보를 가져온 뒤 곧바로 분석과 복기를 이어가게 하는 것입니다. 숫자 Fox ID 는 숫자만 입력할 수 있고 닉네임은 사용할 수 없습니다.
</p>

<p align="center">
  <a href="https://github.com/wimi321/lizzieyzy-next-foxuid/releases">Releases</a>
  ·
  <a href="#한눈에-보기">한눈에 보기</a>
  ·
  <a href="#어떤-패키지를-받아야-하나">패키지 선택</a>
  ·
  <a href="#처음에는-이-순서로-사용">첫 사용 흐름</a>
  ·
  <a href="#빠른-시작">빠른 시작</a>
  ·
  <a href="#문서와-지원">문서</a>
  ·
  <a href="#참여하기">참여하기</a>
</p>

> [!IMPORTANT]
> 바로 쓰고 싶다면 먼저 이 3가지만 보면 됩니다:
> - Windows 는 `windows64.with-katago.installer.exe` 를 먼저 선택
> - Fox 기보를 가져올 때는 **숫자 Fox ID** 만 입력. 숫자만 가능, 닉네임 불가
> - 첫 실행에서는 바로 쓸 수 있도록 분석 환경 준비를 자동으로 먼저 시도

## 한눈에 보기

| 궁금한 점 | 이 유지보수판의 답 |
| --- | --- |
| 내려받은 뒤 바로 열 수 있나 | Windows 는 `installer.exe`, macOS 는 `.dmg`, Linux 는 통합 zip 을 주 추천으로 둡니다 |
| Fox 기보를 지금도 가져올 수 있나 | 고장난 공개 기보 가져오기 흐름을 복구하고 계속 유지보수합니다 |
| 무엇을 입력해야 하나 | 표기를 **숫자 Fox ID** 로 통일하고, 숫자만 가능하며 닉네임은 불가하다고 명확히 씁니다 |
| 첫 실행에서 설정 때문에 멈추지 않나 | 먼저 분석 환경 자동 준비를 시도하므로 대부분의 사용자는 바로 시작할 수 있습니다 |
| 앞으로도 계속 관리되나 | 이 저장소 자체가 계속 릴리스하고 수정하기 위한 유지보수 포크입니다 |

## 실제 화면

![LizzieYzy Next-FoxUID Screenshot](screenshot_ko.png)

## 어떤 패키지를 받아야 하나

> [!TIP]
> 대부분의 사용자는 `with-katago` 를 먼저 고르면 됩니다. `without.engine` 은 엔진을 직접 관리하려는 경우에만 고르세요.

<p align="center">
  <img src="assets/package-guide.svg" alt="LizzieYzy Next-FoxUID Package Guide" width="100%" />
</p>

| 환경 | 추천 패키지 | 런타임 포함 | 바로 분석 가능 | 추천 대상 |
| --- | --- | --- | --- | --- |
| Windows x64 | `windows64.with-katago.installer.exe` | 포함 | 포함 | 다운로드 후 바로 쓰고 싶은 경우 |
| Windows x64 | `windows64.with-katago.portable.zip` | 포함 | 포함 | 설치 없이 바로 시작하고 싶은 경우 |
| Windows x64 | `windows64.without.engine.portable.zip` | 포함 | 없음 | 엔진을 직접 관리하는 경우 |
| macOS Apple Silicon | `mac-arm64.with-katago.dmg` | App 내장 | 포함 | M 시리즈 Mac |
| macOS Intel | `mac-amd64.with-katago.dmg` | App 내장 | 포함 | Intel Mac |
| Linux x64 | `linux64.with-katago.zip` | 포함 | 포함 | Linux 데스크톱 |


현재 공개 release 는 이 6가지 패키지를 중심으로 정리되어 있습니다.

관련 링크:

- [Releases](https://github.com/wimi321/lizzieyzy-next-foxuid/releases)
- [Package Overview (English)](docs/PACKAGES_EN.md)
- [Tested Platforms](docs/TESTED_PLATFORMS.md)

## 처음에는 이 순서로 사용

<p align="center">
  <img src="assets/start-flow.svg" alt="LizzieYzy Next-FoxUID Start Flow" width="100%" />
</p>

대부분의 사용자는 보통 다음 순서로 사용합니다.

1. 내 시스템에 맞는 패키지를 다운로드합니다
2. 앱을 실행하고 Fox 기보 가져오기 메뉴를 엽니다
3. **숫자 Fox ID** 를 입력해 공개 기보를 가져옵니다
4. 그대로 KataGo 분석과 복기를 이어갑니다

아래 짧은 데모 이미지는 그 흐름을 한눈에 보여줍니다.

<p align="center">
  <img src="assets/fox-id-demo.gif" alt="LizzieYzy Next-FoxUID Demo" width="100%" />
</p>

## 빠른 시작

1. [Releases](https://github.com/wimi321/lizzieyzy-next-foxuid/releases) 에서 내 시스템에 맞는 패키지를 다운로드합니다.
2. 가장 빠르게 시작하려면 `with-katago` 를 선택합니다.
3. 앱을 실행하고 Fox 기보 가져오기 메뉴를 엽니다.
4. **숫자 Fox ID** 를 입력해 최신 공개 기보를 가져옵니다.
5. 첫 실행이 OS 에 의해 막히면 [설치 가이드](docs/INSTALL_KO.md) 를 확인합니다.

## 무엇을 할 수 있나

| 용도 | 현재 가능한 기능 |
| --- | --- |
| 기보 가져오기 | 숫자 Fox ID 로 최신 공개 기보 가져오기 |
| 대국 복기 | 승률 변화, 집 차이 변화, 실수 통계, 시각화 |
| 빠른 분석 | KataGo analysis mode 기반 병렬 분석 |
| 배치 처리 | 여러 SGF 일괄 분석 |
| 엔진 비교 | 듀얼 엔진 비교와 엔진 대 엔진 대국 |
| 부분 연구 | 사활 / 부분 국면 연구 보조 |
| 기타 | 판 동기화, 형세 판단 등 원 프로젝트의 주요 기능 |

## 원 프로젝트와의 차이

| 항목 | 원래 LizzieYzy | Next-FoxUID |
| --- | --- | --- |
| Fox 동기화 | 많은 사용자에게 사실상 고장남 | 복구됨 |
| 입력 명칭 | UID / 사용자명 표현이 섞여 있음 | 숫자 Fox ID 로 통일 |
| 배포 구조 | 어떤 파일을 받아야 할지 어려움 | 플랫폼 / 용도 기준으로 재정리 |
| macOS 배포 | 예전 방식이 다소 혼란스러움 | `.dmg` 중심, Apple Silicon / Intel 분리 |
| Windows x64 | 목적별 구분이 약함 | `with-katago` 와 `without.engine` 동시 제공 |
| 유지보수 | 거의 정체 | 지속 유지보수 중 |

## 통합 패키지에 들어 있는 것

| 항목 | 현재 값 |
| --- | --- |
| KataGo 버전 | `v1.16.4` |
| 기본 내장 가중치 | `g170e-b20c256x2-s5303129600-d1228401921.bin.gz` |
| 첫 실행 자동 설정 | 사용 |
| 가중치 다운로드 도우미 | 포함 |

실질적으로는, 주 추천 통합 패키지에 KataGo 와 기본 가중치가 이미 포함되어 있어서 대부분의 사용자는 첫 실행 전에 모델 파일을 따로 찾을 필요가 없습니다.

## 원 프로젝트에서 넘어오는 경우

- Fox 기보 가져오기 흐름은 이제 숫자 Fox ID 기준으로 정리되어 있습니다
- 사용자명 검색은 현재 지원 경로가 아닙니다
- Windows x64 는 `with-katago` 와 `without.engine` 을 둘 다 유지합니다
- macOS 는 추가 `.app.zip` 대신 `.dmg` 중심으로 제공합니다
- 이 저장소는 임시 수정본이 아니라 계속 유지보수하기 위한 포크입니다

## 문서와 지원

- [설치 가이드](docs/INSTALL_KO.md)
- [Troubleshooting (English)](docs/TROUBLESHOOTING_EN.md)
- [Package Overview (English)](docs/PACKAGES_EN.md)
- [Development Guide (English)](docs/DEVELOPMENT_EN.md)
- [Tested Platforms](docs/TESTED_PLATFORMS.md)
- [Support](SUPPORT.md)
- [Changelog](CHANGELOG.md)

## 자주 묻는 질문

<details>
<summary><strong>왜 사용자명 검색을 없앴나요?</strong></summary>

사용자 입장에서도 헷갈렸고 유지보수도 어려웠기 때문입니다. 이 포크에서는 숫자 Fox ID 기준으로 통일합니다.
</details>

<details>
<summary><strong>왜 macOS 에서 app.zip 대신 dmg 중심으로 바꿨나요?</strong></summary>

대부분의 사용자는 바로 설치할 수 있는 형식을 원하기 때문입니다.
</details>

<details>
<summary><strong>내 플랫폼이 Tested Platforms 에 없으면 어떻게 하나요?</strong></summary>

먼저 [Tested Platforms](docs/TESTED_PLATFORMS.md) 와 [설치 가이드](docs/INSTALL_KO.md) 를 확인하세요. 성공이든 실패든 설치 보고를 남겨주면 프로젝트 품질 향상에 직접 도움이 됩니다.
</details>

## 로드맵

- [x] Fox 기보 동기화 복구
- [x] 숫자 Fox ID 기준 흐름으로 통일
- [x] 멀티플랫폼 배포 패키지 재정리
- [x] Intel Mac 패키징 복구
- [x] 설치 / 문제 해결 문서 보강
- [ ] 실기기 설치 보고 더 확보
- [ ] 스크린샷과 소개 자료 추가 개선
- [ ] 한국어 / 일본어 보조 문서 확장

## 참여하기

지금 특히 도움이 되는 기여는 다음과 같습니다.

- Windows / Linux / Intel Mac 실기기 설치 보고
- Fox 동기화 호환성 제보
- 문서, 번역, UI 문구 개선
- 패키징과 릴리스 흐름 수정
- 범위가 작은 코드 수정

링크:

- [Contributing Guide](CONTRIBUTING.md)
- [Code Of Conduct](CODE_OF_CONDUCT.md)
- [Security Policy](SECURITY.md)
- [Support](SUPPORT.md)
- [Issues](https://github.com/wimi321/lizzieyzy-next-foxuid/issues)
- [Discussions](https://github.com/wimi321/lizzieyzy-next-foxuid/discussions)

## 크레딧

- Original project: [yzyray/lizzieyzy](https://github.com/yzyray/lizzieyzy)
- Upstream GUI base: [featurecat/lizzie](https://github.com/featurecat/lizzie)
- Engine: [lightvector/KataGo](https://github.com/lightvector/KataGo)

## 라이선스

원 프로젝트의 라이선스를 그대로 따릅니다. 자세한 내용은 [LICENSE.txt](LICENSE.txt) 를 참고하세요.
