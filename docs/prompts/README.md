# プロンプト

このディレクトリには、AI支援開発で使用するプロンプトテンプレートを保存します。

これらのテンプレートは、AIへの指示を標準化して一貫性を高め、高品質なAI支援開発ワークフローの維持に役立ちます。

---

# 目的

- AI assistantへの指示を標準化する
- タスクごとにプロンプトを一から作成することを避ける
- 一貫した開発ワークフローを維持する
- プロンプトの品質を継続的に改善する

---

# テンプレート

## `issue-prompt-template.md`

GitHub Issueの生成・作成・更新を安全に行うためのプロンプトです。

## `approved-design-prompt-template.md`

実装開始前にApproved Design文書を生成します。

## `pull-request-draft-prompt-template.md`

Pull Requestのドラフト説明を生成します。

## `generate-git-metadata-prompt-template.md`

AI支援ワークフローの引き継ぎとレビュー用にGitメタデータの概要を生成します。

## `github-read-prompt-template.md`

GitHub上のIssue、Pull Request、リポジトリ状態などを読み取り、確認するためのプロンプトです。

## `repository-documentation-update-prompt-template.md`

実装後にリポジトリドキュメントをレビューし、必要に応じて影響を受ける文書を更新します。

## `repository-review-prompt-template.md`

ファイルを変更せずに現在のリポジトリ状態をレビューします。

---

# ワークフロー

GitHub Issue
↓
Approved Design（必要な場合）
↓
Implementation
↓
Repository Documentation Review
↓
Repository Review（必要な場合）
↓
Pull Request Draft

---

# ルール

- 可能な限り再利用できるようにプロンプトを設計します。
- プロンプトの変更時は、既存の安全性・承認ルールを不用意に削除しません。
- リポジトリ固有の要件がある場合は、必要な範囲でテンプレートを調整します。

---

# ドキュメント作成ガイドライン

- この`README.md`は日本語で記述します。
- READMEの新規および変更する内容は、原則として日本語で記述します。
- ファイル名、ディレクトリ名、コマンド、コード、識別子、技術固有名詞は必要に応じて英語のまま維持します。
- READMEは簡潔にし、そのディレクトリの目的に焦点を当てます。
