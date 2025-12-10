# Fetch Server 실행 방법

## 문제 해결

IDE에서 직접 실행할 때 `SpringApplication cannot be resolved` 에러가 발생하는 경우:

### 원인
IDE가 Gradle 프로젝트를 제대로 인식하지 못해 Spring Boot 의존성이 클래스패스에 포함되지 않습니다.

### 해결 방법

#### 방법 1: Gradle로 직접 실행 (가장 확실)

터미널에서 실행:

```bash
cd /Users/shin-uijin/BITRIEVER/fetch-server
./gradlew bootRun
```

또는 빌드된 JAR로 실행:

```bash
cd /Users/shin-uijin/BITRIEVER/fetch-server
java -jar build/libs/fetch-server-0.0.1-SNAPSHOT.jar
```

#### 방법 2: IDE 설정 재설정

1. **IDE 완전 종료**
2. **Java Language Server 워크스페이스 정리**
   - `Cmd+Shift+P` (Mac) 또는 `Ctrl+Shift+P` (Windows/Linux)
   - "Java: Clean Java Language Server Workspace" 실행
   - "Restart and delete" 선택
3. **IDE 재시작**
4. **프로젝트 다시 열기**
   - `File` → `Open Folder` → `fetch-server` 선택
5. **Gradle 프로젝트로 인식 대기**
   - 하단 상태바에서 "Java Projects" 클릭
   - "Reload Projects" 선택
6. **실행**
   - `F5` 또는 실행 버튼
   - "Launch FetchServerApplication (Gradle)" 선택

#### 방법 3: IDE에서 Gradle Task 실행

1. `Cmd+Shift+P` → "Tasks: Run Task"
2. "gradle: bootRun" 선택

## 서버 확인

서버가 정상 실행되면:
- **포트**: `http://localhost:8081`
- **Swagger UI**: `http://localhost:8081/docs`
- **Health Check**: `http://localhost:8081/health`

## 참고

- IDE에서 직접 실행할 때 발생하는 에러는 IDE의 클래스패스 인식 문제입니다.
- Gradle로 실행하면 정상적으로 동작합니다.
- 프로덕션 환경에서는 빌드된 JAR 파일로 실행하는 것을 권장합니다.

