moved {
  from = aws_iam_role.github_terraform_apply
  to   = aws_iam_role.github_terraform_apply[0]
}

moved {
  from = aws_iam_role_policy_attachment.github_apply_power_user
  to   = aws_iam_role_policy_attachment.github_apply_power_user[0]
}

moved {
  from = aws_iam_role_policy.github_apply_iam
  to   = aws_iam_role_policy.github_apply_iam[0]
}

moved {
  from = aws_iam_role_policy.github_apply_state
  to   = aws_iam_role_policy.github_apply_state[0]
}

moved {
  from = aws_iam_role.github_deploy
  to   = aws_iam_role.github_deploy[0]
}

moved {
  from = aws_iam_role_policy.github_deploy
  to   = aws_iam_role_policy.github_deploy[0]
}

moved {
  from = aws_iam_role.github_dev_deploy
  to   = aws_iam_role.github_dev_deploy[0]
}

moved {
  from = aws_iam_role_policy.github_dev_deploy
  to   = aws_iam_role_policy.github_dev_deploy[0]
}

moved {
  from = aws_iam_role.github_terraform_dev_apply
  to   = aws_iam_role.github_terraform_dev_apply[0]
}

moved {
  from = aws_iam_role_policy_attachment.github_dev_apply_power_user
  to   = aws_iam_role_policy_attachment.github_dev_apply_power_user[0]
}

moved {
  from = aws_iam_role_policy.github_dev_apply_iam
  to   = aws_iam_role_policy.github_dev_apply_iam[0]
}

moved {
  from = aws_iam_role_policy.github_dev_apply_state
  to   = aws_iam_role_policy.github_dev_apply_state[0]
}