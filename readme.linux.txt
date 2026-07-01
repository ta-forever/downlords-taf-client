QUICK START FOR LINUX


1. Install dependencies (examples given are for Ubuntu)

   $ sudo apt-get install qtbase5-dev zlib1g git git-lfs wine winetricks openjdk-21-jre

2.  ONLY IF USING WINE VERSION 9 OR LOWER. OTHERWISE, SKIP TO STEP 3.
    Configure  32 bit wine and  install  directplay.  
    This is best done with a fresh  WINEPREFIX.  Remember  to set  WINEPREFIX=~/.tafwine  
    when  running the client in the future or at session startup.
    
   $ export WINEPREFIX=~/.tafwine
   $ WINEARCH=win32 winecfg
   
3. Install directplay

   $ winetricks directplay

3. Run the cnc-ddraw config tool.
   Without the appropriate DLL overrides, cnc-ddraw won't be used when you
   run TA. Running the config tool will set the overrides and let you
   configure your graphics options.
   
   $ cd ~/games/TA
   $ wine cnc-ddraw\ config.exe
   
4. Check that you can play TA independently of TAF.  Try different compatibility settings in winecfg if you have to.
   (see https://www.tauniverse.com/forum/showthread.php?t=46469)
   (the chmod I don't think is required for wine, but TAF requires it)

   $ cd ~/games/TA
   $ chmod +x TotalA.exe
   $ wine TotalA.exe

5. Extract tarball (ensure path matches your download archive)

   $ cd ~
   $ tar -zxf Downloads/tafclient_unix_2026_6_15.tar.gz 
   $ cd tafclient_unix_2026_6_15.tar.gz 

6. Check dependencies.
   following commands should display command line options for the tools.
   If they instead complain about missing libraries, use apt-get to install them.

   $ natives/bin/gpgnet4ta --help
   $ natives/bin/maptool --help
   $ wine natives/bin/talauncher.exe --help

7. Start TAF client

   $ ./taf-java-client
