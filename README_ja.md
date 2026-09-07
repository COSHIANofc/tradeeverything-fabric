# TradeEverything

TradeEverything は Minecraft 26.2 向け Fabric MOD です。新規生成されるバニラの**沼地の小屋**では、構造物が生成する Witch を canonical な TradeEverything Villager 商人に置き換えます。小屋本体と Cat の生成はバニラのままで、既に生成済みの小屋は安全のため変更しません。

商人は、Survival で入手可能か監査された有効なバニラアイテムをサーバー権威で売買する検索可能な画面を開きます。一覧は一度に9行を表示してスクロールでき、ローカライズ名・レジストリ ID で検索できます。すべてのバニラエンチャントには最大レベルのエンチャントの本を用意し、`修繕`、`Mending`、`minecraft:mending` のように検索できます。

## 必要環境と導入

Minecraft 26.2、Java 25、Fabric Loader 0.19.3 以上、Fabric API 0.157.0+26.2 以上の互換版、TradeEverything 0.7.a-dev が、クライアントとサーバーの両方で必要です。Fabric API と `tradeeverything-0.7.a-dev.jar` を両方の `mods` ディレクトリに入れてください。

## コマンド

権限レベル2が必要です。

```text
/tre summon [<x> <y> <z>]
/tre verify
/tre reload
```

`summon` は同一の canonical 商人を作る管理・デバッグ用コマンドです。沼地の小屋での置換確認には使えません。

## 進捗

TradeEverything は4つの進捗を追加します。いずれもサーバーが成功と確定した商人との取引後にのみ付与されます。全アイテムの進捗は現在有効なカタログのアイテム ID をワールドの永続データに記録します。中身入りシュルカーボックスを売った場合は中身を記録し、手元に残る箱本体は記録しません。

## 開発

検証済みの最低基準は Minecraft 26.2、Fabric Loader 0.19.3、Fabric API 0.157.0+26.2、Fabric Loom 1.17.2、Gradle 9.7.1、Java 25 です。`./gradlew clean build` で `build/libs/tradeeverything-0.7.a-dev.jar` を生成します。

## ライセンス

Copyright 2026 COSHIAN。 [Apache License 2.0](LICENSE) を適用します。
