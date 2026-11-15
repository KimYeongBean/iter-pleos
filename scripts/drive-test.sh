#!/usr/bin/env bash
# Iter 주행 테스트: Pleos Connect 에뮬레이터에서 주행 하나를 만들어 Iter가 기록하게 합니다.
#
# 서울 시청 부근을 트랙처럼 한 바퀴 돌며 속도를 바꾸고, 도중에 방향지시등·와이퍼·전조등을 켭니다.
# 다 달리면 정차해 P로 두고 60초를 기다립니다. 그러면 주행 기록이 끝나 날짜별 기록의 오늘 칸에 나타납니다.
#
# 필요한 것: 에뮬레이터가 켜져 있고, Iter를 설치해 처음 실행(동의·권한)까지 마친 상태. adb, curl, awk.
# Windows에서는 Git Bash에서 실행합니다.
#
# 사용법: bash scripts/drive-test.sh [주행 초, 기본 120]
set -euo pipefail

DRIVE_SECONDS="${1:-120}"
VC="http://127.0.0.1:8088"
CLIENT="iter-drive-test"
START_LON=126.9780
START_LAT=37.5665

# Virtual Console은 브라우저 페이지가 열려 있을 때만 차량 값을 받습니다. 페이지 대신 1초마다 같은 신호를 보냅니다.
alive() { curl -fsS -X POST "$VC/browser/session?client=$CLIENT" >/dev/null; }
put() { curl -fsS -X PUT "$VC$1" >/dev/null; }
geo() { adb emu geo fix "$1" "$2" >/dev/null; }

# t초 때의 위치(경도 위도): 시작점을 지나는 타원 트랙 한 바퀴.
point() {
    awk -v t="$1" -v n="$DRIVE_SECONDS" -v lo="$START_LON" -v la="$START_LAT" 'BEGIN {
        a = 2 * 3.14159265 * t / n
        printf "%.6f %.6f", lo + 0.004 * sin(a) + 0.0006 * sin(3 * a), la + 0.002 * (1 - cos(a))
    }'
}

# t초 때의 차속(km/h): 40~70 사이를 오르내립니다.
speed() {
    awk -v t="$1" 'BEGIN { printf "%d", 55 + 15 * sin(t / 9) }'
}

adb forward tcp:8088 tcp:8088 >/dev/null
if ! alive; then
    echo "Virtual Console(http://127.0.0.1:8088)에 연결하지 못했습니다. 에뮬레이터가 켜져 있는지 확인하세요." >&2
    exit 1
fi

echo "주행 시작: 기어 D, ${DRIVE_SECONDS}초 동안 달립니다."
echo "  달리는 동안 앱 화면은 시스템이 가립니다(Unavailable while driving). 기록은 뒤에서 계속됩니다."
geo "$START_LON" "$START_LAT"
alive
put "/vehicle/gear?gear=Drive"

for ((t = 0; t < DRIVE_SECONDS; t++)); do
    alive
    case $t in
        15) put "/vehicle/turn-signal?mode=BlinkLeft"; echo "  ${t}초: 왼쪽 방향지시등" ;;
        25) put "/vehicle/turn-signal?mode=Off" ;;
        40) put "/vehicle/body?front_wiper=Low"; echo "  ${t}초: 와이퍼 켬" ;;
        65) put "/vehicle/body?front_wiper=Off" ;;
        75) put "/vehicle/turn-signal?mode=BlinkRight"; echo "  ${t}초: 오른쪽 방향지시등" ;;
        85) put "/vehicle/turn-signal?mode=Off" ;;
        90) put "/vehicle/lights?feature=headlamp_mode&value=LowBeam"; echo "  ${t}초: 전조등(로우빔) 켬" ;;
    esac
    put "/vehicle/controls?speed=$(speed "$t")&accelerator=30&brake=0&steering=0"
    # shellcheck disable=SC2046
    geo $(point "$t")
    sleep 1
done

echo "정차: 속도를 줄이고 기어를 P로 둡니다."
put "/vehicle/lights?feature=headlamp_mode&value=Off"
for s in 40 25 10 0; do
    alive
    put "/vehicle/controls?speed=$s&accelerator=0&brake=40&steering=0"
    sleep 1
done
put "/vehicle/controls?speed=0&accelerator=0&brake=0&steering=0"
put "/vehicle/gear?gear=Park"

echo "P에서 60초를 기다리면 주행 기록이 끝납니다 (70초 대기)."
for ((w = 70; w > 0; w--)); do
    alive
    if ((w % 10 == 0)); then echo "  ${w}초 남음"; fi
    sleep 1
done

echo "완료. 앱의 날짜별 기록에서 오늘 칸을 누르면 맵 그래프와 하루 그림이 보입니다."
