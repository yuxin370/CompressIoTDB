
# echo "start-evaluate-baseline."
# sh benchmark.sh -cf ./conf/config_iotdb/config7.properties >> res/res7
# echo "res7_baseline done"
# sh benchmark.sh -cf ./conf/config_iotdb/config5.properties >> res/res5
# echo "res5_baseline done"
# sh benchmark.sh -cf ./conf/config_iotdb/config3.properties >> res/res3
# echo "res3_baseline done"
# sh benchmark.sh -cf ./conf/config_iotdb/config.properties  >> res/res1
# echo "res1_baseline done"
# sh benchmark.sh -cf ./conf/config_iotdb/config9.properties >> res/res9
# echo "res9_baseline done"


# echo "start-evaluate-baseline_plain."
# sh benchmark.sh -cf ./conf/config_plain/config7_p.properties >> res/res7
# echo "res7_p done"
# sh benchmark.sh -cf ./conf/config_plain/config5_p.properties >> res/res5
# echo "res5_p done"
# sh benchmark.sh -cf ./conf/config_plain/config3_p.properties >> res/res3
# echo "res3_p done"
# sh benchmark.sh -cf ./conf/config_plain/config_p.properties  >> res/res1
# echo "res1_p done"
# sh benchmark.sh -cf ./conf/config_plain/config9_p.properties >> res/res9
# echo "res9_p done"

# echo "start-evaluate-ours."
# sh benchmark.sh -cf ./conf/config_ours/config7_ours.properties >> res/res7
# echo "res7_ours done"
# sh benchmark.sh -cf ./conf/config_ours/config5_ours.properties >> res/res5
# echo "res5_ours done"
# sh benchmark.sh -cf ./conf/config_ours/config3_ours.properties >> res/res3
# echo "res3_ours done"
# sh benchmark.sh -cf ./conf/config_ours/config_ours.properties  >> res/res1
# echo "res1_ours done"
# sh benchmark.sh -cf ./conf/config_ours/config9_ours.properties >> res/res9
# echo "res9_ours done"


# echo "start-evaluate-nolazy."
# sh benchmark.sh -cf ./conf/config_nolazy/config7_nolazy.properties >> res/res7
# echo "res7_nolazy done"
# sh benchmark.sh -cf ./conf/config_nolazy/config5_nolazy.properties >> res/res5
# echo "res5_nolazy done"
# sh benchmark.sh -cf ./conf/config_nolazy/config3_nolazy.properties >> res/res3
# echo "res3_nolazy done"
# sh benchmark.sh -cf ./conf/config_nolazy/config_nolazy.properties  >> res/res1
# echo "res1_nolazy done"
# sh benchmark.sh -cf ./conf/config_nolazy/config9_nolazy.properties >> res/res9
# echo "res9_nolazy done"

# echo "start-evaluate-noindex."
# sh benchmark.sh -cf ./conf/config_noindex/config7_noindex.properties >> res/res7
# echo "res7_noindex done"
# sh benchmark.sh -cf ./conf/config_noindex/config5_noindex.properties >> res/res5
# echo "res5_noindex done"
# sh benchmark.sh -cf ./conf/config_noindex/config3_noindex.properties >> res/res3
# echo "res3_noindex done"
# sh benchmark.sh -cf ./conf/config_noindex/config_noindex.properties  >> res/res1
# echo "res1_noindex done"
# sh benchmark.sh -cf ./conf/config_noindex/config9_noindex.properties >> res/res9
# echo "res9_noindex done"


echo "start-evaluate-modi."
sh benchmark.sh -cf ./conf/config_modi/config7_modi.properties >> res/res7
echo "res7_modi done"
sh benchmark.sh -cf ./conf/config_modi/config3_modi.properties >> res/res3
echo "res3_modi done"
sh benchmark.sh -cf ./conf/config_modi/config5_modi.properties >> res/res5
echo "res5_modi done"
sh benchmark.sh -cf ./conf/config_modi/config_modi.properties  >> res/res1
echo "res1_modi done"
sh benchmark.sh -cf ./conf/config_modi/config9_modi.properties >> res/res9
echo "res9_modi done"


cd res
python res_parse.py
cd ..



