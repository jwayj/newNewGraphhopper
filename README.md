#newNewGraphhopper 실행 방법

1. https://github.com/jwayj/newNewGraphhopper.git  git clone하기
2. (mvn이 없을 경우) [Download Apache Maven – Maven](https://maven.apache.org/download.cgi) Binary zip archive 파일 다운로드 후 환경변수 설정
3. 터미널에서 해당 디렉토리로 이동 후, mvn clean install -DskipTests 명령어 실행
4. 터미널에서 newNewGraphhopper/backend로 이동
5. 첨부한 파일을 위 디렉토리에 넣음
6. (npm이 없으면) [Node.js — 어디서든 JavaScript를 실행하세요](https://nodejs.org/ko) 설치
7. npm init -y 실행
8. npm install firebase-admin 실행
9. 위 과정이 끝나면 3번 과정 한 번 더 실행
10. 콘솔창을 열어서 ngrok http --domain=alpaca-worthy-polecat.ngrok-free.app 4567 실행해서 서버 오픈
11. vscode에서 [RoutingExample.java](http://RoutingExample.java) 파일을 실행하면 됨
