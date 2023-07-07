@echo off

set logger.type=window
call "%~dp0set-env.cmd"

set working-path.integral-system-analysis.folder=Software\config\integralSystemsAnalysis

if [%logger.type%] == [window] (
	set lottery.application.name=SE Lottery integral systems analyzer
	set logger.window.bar.background-color=229,61,48
	set logger.window.bar.text-color=253,195,17
	start "%secondWindowTile%" /D "%~dp0" "%JAVA_HOME%\bin\%JAVA_COMMAND%" -Xmx%XMX% -cp %classPath%;%LIBS%;"%CURRENT_DIR%binaries.jar"; org.rg.game.lottery.application.SEIntegralSystemAnalyzer
) else (
	call "%JAVA_HOME%\bin\%JAVA_COMMAND%" -Xmx%XMX% -cp %classPath%;%LIBS%;"%CURRENT_DIR%binaries.jar"; org.rg.game.lottery.application.SEIntegralSystemAnalyzer
	pause
)