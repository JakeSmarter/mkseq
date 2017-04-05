REM Copyright 2016 Mapillary AB, Sweden
REM
REM Licensed under the Apache License, Version 2.0 (the "License");
REM you may not use this file except in compliance with the License.
REM You may obtain a copy of the License at
REM
REM     https://www.apache.org/licenses/LICENSE-2.0
REM
REM Unless required by applicable law or agreed to in writing, software
REM distributed under the License is distributed on an "AS IS" BASIS,
REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
REM See the License for the specific language governing permissions and
REM limitations under the License.

REM TODO: Add "-J" Java VM passthrough option support (may require the Windows
REM Script Host) but tokenization of IF /F may work too.
@ECHO OFF
IF %1 == "" ^
javaw -client -Xss2M -Xms32M -Xmx1G ^
-XX:CompileThreshold=1000 -XX:+AggressiveHeap -XX:+UseStringDeduplication ^
-jar "%~dp0\mkseq.jar" ^
%*
ELSE ^
java -client -Xss2M -Xms32M -Xmx1G ^
-XX:CompileThreshold=1000 -XX:+AggressiveHeap -XX:+UseStringDeduplication ^
-jar "%~dp0\mkseq.jar" ^
%*