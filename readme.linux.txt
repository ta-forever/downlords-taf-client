QUICK START


1. install dependencies

   $ sudo apt-get install openjdk-15-jre qt5-default zlib1g

2. setup wine and check that you can play TA independently of TAF
   (see https://www.tauniverse.com/forum/showthread.php?t=46469)
   (the chmod I don't think is required for wine, but TAF requires it)

   $ cd ~/games/TA
   $ chmod +x TotalA.exe
   $ wine TotalA.exe
    
2. extract tarball

   $ cd ~
   $ tar -zxf Downloads/tafclient_unix_1_4_3-taf-0_13_1.tar.gz
   $ cd downlords-taf-client-1.4.3-taf-0.13.1

3. check dependencies.
   following commands should display command line options for the tools.
   If they instead complain about missing libraries, use apt-get to install them.

   $ natives/bin/gpgnet4ta --help
   $ natives/bin/maptool --help
   $ wine natives/bin/talauncher.exe --help

4. start TAF client

   $ ./downlords-taf-client
