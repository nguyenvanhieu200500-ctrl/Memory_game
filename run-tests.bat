@echo off
setlocal
cd /d "%~dp0"
set MEMORY_GAME_STORAGE=file
call "%~dp0compile.bat"
if errorlevel 1 exit /b 1
javac -encoding UTF-8 -cp out -d out test\ComboContrastTest.java test\ShopEffectsTest.java test\SmokeTest.java test\ChatTest.java test\SocialTest.java test\WhisperHistoryTest.java test\UnreadMessagesTest.java test\StreamingMusicTest.java test\ChatPanelTest.java test\MatchmakingRulesTest.java test\FullGameReplayTest.java test\ServerMonitorViewTest.java test\HistoryPanelTest.java test\RankingPanelTest.java test\CardThemeTest.java test\CreateRoomPanelTest.java test\RoomInvitePanelTest.java test\InvitationPanelTest.java test\RoomSettingsTest.java test\TestSuite.java
if errorlevel 1 exit /b 1
java -cp out memorygame.test.TestSuite 12347
