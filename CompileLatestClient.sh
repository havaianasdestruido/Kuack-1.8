#!/bin/sh
java -Deaglercraft.TeaVMBridge=buildtools/TeaVMBridge.jar \
  -cp "buildtools/BuildTools.jar:buildtools/deps/*" \
  net.lax1dude.eaglercraft.v1_8.buildtools.gui.CompileLatestClientGUI
rm -rf "##TEAVM.TMP##"