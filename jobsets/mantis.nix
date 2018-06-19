{ stdenv, mantisSrc, scala, sbt, callPackage, unzip, sbtVerify }:

stdenv.mkDerivation {
  name = "mantis";
  src = mantisSrc;

  buildInputs = [ scala sbt sbtVerify unzip ];

  outputs = [ "out" "zip" ];

  configurePhase = ''
    export HOME="$NIX_BUILD_TOP"
    cp -r ${sbtVerify} target
    chmod -R u+w target
  '';

  buildPhase = ''
    sbt 'set test in Test := {}' dist
  '';

  installPhase = ''
    cp target/universal/mantis-1.0-daedalus-rc1.zip $zip

    mkdir $out
    unzip $zip
    mv mantis-1.0-daedalus-rc1/* $out
  '';
}
