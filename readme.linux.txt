QUICK START FOR UBUNTU 22.04 LTS


1. Install dependencies

   $ sudo apt-get install qtbase5-dev zlib1g git git-lfs wine winetricks openjdk-18-jre

2. Configure 32 bit wine and install directplay

   $ WINEARCH=win32 winecfg
   $ winetricks directplay

3. Check that you can play TA independently of TAF.  Try different compatibility settings in winecfg if you have to.
   (see https://www.tauniverse.com/forum/showthread.php?t=46469)
   (the chmod I don't think is required for wine, but TAF requires it)

   $ cd ~/games/TA
   $ chmod +x TotalA.exe
   $ wine TotalA.exe
    
4. Extract tarball

   $ cd ~
   $ tar -zxf Downloads/tafclient_unix_1_4_3-taf-0_15_1.tar.gz
   $ cd downlords-taf-client-1.4.3-taf-0.15.1

5. Check dependencies.
   following commands should display command line options for the tools.
   If they instead complain about missing libraries, use apt-get to install them.

   $ natives/bin/gpgnet4ta --help
   $ natives/bin/maptool --help
   $ wine natives/bin/talauncher.exe --help

6. Start TAF client

   $ ./downlords-taf-client
